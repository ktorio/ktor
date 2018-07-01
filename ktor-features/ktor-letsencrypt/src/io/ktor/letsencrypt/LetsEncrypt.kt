package io.ktor.letsencrypt

import io.ktor.application.*
import io.ktor.pipeline.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.util.*
import kotlinx.coroutines.experimental.*
import org.shredzone.acme4j.*
import org.shredzone.acme4j.challenge.*
import org.shredzone.acme4j.util.*
import org.slf4j.*
import java.io.*
import java.security.*
import java.security.cert.*


class LetsEncrypt(val config: Configuration) {
    data class DomainSet(val config: Configuration, val domains: List<String>, val organization: String) {
        val mainDomain = domains.first()

        val domainFolder by lazy {
            File(config.certFolder, mainDomain).apply { mkdirs() }
        }
    }

    class Configuration {
        //var acmeEndPoint = "https://acme-v01.api.letsencrypt.org/directory"
        //var acmeDirectoryEndPoint = "https://acme-staging.api.letsencrypt.org/directory"
        internal var kind = "staging"
        val suffix get() = if (kind == "staging") "-staging" else "1"
        internal var acmeDirectoryEndPoint = "https://acme-staging-v02.api.letsencrypt.org/directory"
        //val acmeDirectoryEndPoint = "acme://letsencrypt.org/staging"
        //var engine: HttpClientEngine = Apache.create { }
        var certFolder = File("./certs")
        var keySize = 4096
        var sslPort = 443
        var sslHost = "0.0.0.0"
        lateinit var email: String
        internal val domains = arrayListOf<DomainSet>()

        fun setProduction() {
            kind = "production"
            acmeDirectoryEndPoint = "https://acme-v02.api.letsencrypt.org/directory"
        }

        fun setStaging() {
            kind = "staging"
            acmeDirectoryEndPoint = "https://acme-staging-v02.api.letsencrypt.org/directory"
        }

        /**
         * A domain that will require HTTPS. Ktor must be processing HTTP calls for this domain already.
         */
        fun addDomainSet(mainDomain: String, vararg extraDomains: String, organization: String = "myorganization") {
            domains += DomainSet(this, listOf(mainDomain) + extraDomains, organization)
        }
    }

    companion object Feature : ApplicationFeature<Application, Configuration, LetsEncrypt> {
        override val key = AttributeKey<LetsEncrypt>(LetsEncrypt::class.simpleName!!)
        override fun install(pipeline: Application, configure: Configuration.() -> Unit): LetsEncrypt {
            val feature = LetsEncrypt(
                Configuration().apply(configure)
            )
            pipeline.intercept(ApplicationCallPipeline.Infrastructure) { feature.intercept(this) }
            pipeline.environment.monitor.subscribe(ApplicationStarted) {
                launch(newSingleThreadContext("ConfigureLetsEncrypt")) {
                    feature.applicationStarted(it)
                }
            }
            return feature
        }
    }

    private fun intercept(context: PipelineContext<Unit, ApplicationCall>) {
        context.application.routing {
            // https://ietf-wg-acme.github.io/acme/draft-ietf-acme-acme.html#http-challenge
            get("/.well-known/acme-challenge/{token}") {
                val host = call.request.host() ?: error("No host!")
                val token = call.parameters["token"]
                val content = tokens[host]?.get(token) ?: "error"
                call.respondText(content)
            }
        }
    }

    val tokens = LinkedHashMap<String, LinkedHashMap<String, String>>()

    val DomainSet.crtFile get() = File(domainFolder, "chain${config.suffix}.pem")
    val DomainSet.csrFile get() = File(domainFolder, "request${config.suffix}.pem")
    val DomainSet.keyFile get() = File(domainFolder, "privkey${config.suffix}.pem")

    val userCertFile = File(config.certFolder.apply { mkdirs() }, "user-privkey.pem")

    val session = Session(config.acmeDirectoryEndPoint)

    private val account by lazy {
        val email = config.email

        if (!userCertFile.exists()) {
            val keyPair = generateKeyPair(config.keySize)
            userCertFile.writeBytes(keyPair.toByteArray())
        }

        val keyPair = KeyPair(userCertFile.readBytes())

        val login = AccountBuilder()
            .addContact("mailto:$email")
            .agreeToTermsOfService()
            .useKeyPair(keyPair)
            .createLogin(session)

        login.account
    }

    val logger = LoggerFactory.getLogger("LetsEncrypt")

    fun applicationStarted(application: Application) {
        for (domainSet in config.domains) {
            logger.info("Processing $domainSet")
            val order = account.newOrder().domains(domainSet.domains).create()
            val crtFile = domainSet.crtFile
            val csrFile = domainSet.csrFile

            logger.trace("${order.authorizations}")

            for (auth in order.authorizations) {
                logger.trace("$domainSet: auth.status=${auth.status}")
                if (auth.status != Status.VALID) {
                    val challenge =
                        auth.findChallenge<Http01Challenge>(Http01Challenge.TYPE) ?: error("Can't find http challenge")

                    val domainMap = tokens.getOrPut(auth.domain) { LinkedHashMap() }
                    domainMap[challenge.token] = challenge.authorization

                    challenge.trigger()

                    logger.trace("auth.status: ${auth.status}")
                    var count = 0
                    while (auth.status != Status.VALID) {
                        logger.trace("auth.status: ${auth.status}")
                        Thread.sleep(6000L)
                        auth.update()
                        count++
                        if (auth.status == Status.INVALID) error("Invalid auth")
                        if (count >= 10) error("Couldn't process")
                    }

                    val domainKeyPair = generateKeyPair(config.keySize)

                    domainSet.keyFile.writeBytes(domainKeyPair.toByteArray())


                    logger.trace("Creating $csrFile...")

                    val csrb = CSRBuilder()
                    for (domain in domainSet.domains) {
                        csrb.addDomain(domain)
                    }
                    csrb.setOrganization(domainSet.organization)
                    csrb.sign(domainKeyPair)
                    val csr = csrb.encoded

                    csrb.write(FileWriter(csrFile))

                    order.execute(csr)

                    logger.trace("CERT.order.status: ${order.status}")
                    while (order.status != Status.VALID) {
                        logger.info("CERT.order.status: ${order.status}")
                        Thread.sleep(3000L)
                        order.update()
                    }

                    val cert = order.certificate ?: error("Can't download certificate chain!")
                    FileWriter(crtFile).use { cert.writeCertificate(it) }
                }
            }

            val ndomainSet = config.domains.first()

            val keystoreAlias = "ktor-alias"
            val cert = loadPublicX509(ndomainSet.crtFile.readBytes())
            val privateKey = KeyPair(ndomainSet.keyFile.readBytes()).private

            val keyStore = KeyStore.getInstance(KeyStore.getDefaultType())

            keyStore.load(null, null)
            keyStore.setEntry(
                keystoreAlias,
                KeyStore.PrivateKeyEntry(privateKey, arrayOf(cert)),
                KeyStore.PasswordProtection(charArrayOf())
            )

            logger.info("Requesting update certificate for $domainSet - ${config.sslHost}:${config.sslPort}")

            application.environment.monitor.raise(RequestUpdateConnectors, UpdateConnectors(
                listOf(
                    EngineSSLConnectorBuilder(
                        keyStore,
                        keystoreAlias,
                        { charArrayOf() },
                        { charArrayOf() }).apply {
                        port = config.sslPort
                        host = config.sslHost
                    }),
                listOf()
            ))

            // @TODO: Renewal!
        }
    }
}


private fun generateKeyPair(certsize: Int) =
    KeyPairGenerator.getInstance("RSA").apply { initialize(certsize, SecureRandom()) }.generateKeyPair()

private fun KeyPair(bytes: ByteArray): KeyPair =
    StringReader(bytes.toString(Charsets.UTF_8)).use { KeyPairUtils.readKeyPair(it) }

private fun KeyPair.toByteArray(): ByteArray =
    CharArrayWriter().apply { KeyPairUtils.writeKeyPair(this@toByteArray, this) }.toString().toByteArray(Charsets.UTF_8)

private fun loadPublicX509(bytes: ByteArray): X509Certificate? =
    CertificateFactory.getInstance("X.509").generateCertificate(ByteArrayInputStream(bytes)) as X509Certificate
