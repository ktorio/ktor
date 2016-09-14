package org.jetbrains.ktor.testing

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.host.*
import org.jetbrains.ktor.pipeline.*
import org.jetbrains.ktor.routing.*
import org.junit.*
import org.slf4j.*
import sun.security.x509.*
import java.io.*
import java.math.*
import java.net.*
import java.security.*
import java.time.*
import java.util.*
import java.util.concurrent.*
import javax.net.ssl.*
import kotlin.concurrent.*

abstract class HostTestBase<H : ApplicationHost> {
    protected val port = findFreePort()
    protected val sslPort = findFreePort()
    protected var server: H? = null

    val testLog : Logger = LoggerFactory.getLogger("HostTestBase")

    @Before
    fun setUpBase() {
        testLog.trace("Starting server on port $port (SSL $sslPort)")
    }

    @After
    fun tearDownBase() {
        (server as? ApplicationHostStartable)?.stop()
    }

    protected abstract fun createServer(envInit: ApplicationEnvironmentBuilder.() -> Unit, block: Routing.() -> Unit): H

    protected fun createAndStartServer(envInit: ApplicationEnvironmentBuilder.() -> Unit = {}, block: Routing.() -> Unit): H {
        val server = createServer(envInit, block)
        startServer(server)

        return server
    }

    protected fun startServer(server: H) {
        this.server = server
        val l = CountDownLatch(1)
        thread {
            l.countDown()
            (server as? ApplicationHostStartable)?.start()
        }
        l.await()

        waitForPort(port)
        //waitForPort(sslPort)
    }

    protected fun findFreePort() = ServerSocket(0).use { it.localPort }
    protected fun withUrl(path: String, block: HttpURLConnection.(Int) -> Unit) {
        withUrl(URL("http://127.0.0.1:$port$path"), port, block)
        withUrl(URL("https://127.0.0.1:$sslPort$path"), sslPort, block)
    }

    protected fun withUrlHttp2(path: String, block: Unit.(Int) -> Unit) {
        withHttp2(URL("http://127.0.0.1:$port$path"), port, block)
//        withHttp2(URL("https://127.0.0.1:$sslPort$path"), sslPort, block)
    }

    private fun withUrl(url: URL, port: Int, block: HttpURLConnection.(Int) -> Unit) {
        val connection = url.openConnection() as HttpURLConnection
        if (connection is HttpsURLConnection) {
            connection.sslSocketFactory = sslSocketFactory
        }
        connection.connectTimeout = 10000
        connection.readTimeout = 30000
        connection.instanceFollowRedirects = false

        connection.block(port)
    }

    private fun withHttp2(url: URL, port: Int, block: Unit.(Int) -> Unit) {
//        val transport = HTTP2Client()
//        val client = HttpClient(HttpClientTransportOverHTTP2(transport), null)
//
//        transport.start()
//        client.start()
//
//        try {
//            client.GET(url.toURI()).apply {
//                assertEquals("HTTP/2.0", version.asString())
//                block(port)
//            }
//        } finally {
//            client.stop()
//            transport.stop()
//        }
    }

    protected fun PipelineContext<*>.failAndProceed(e: Throwable): Nothing {
        runBlock { fail(e) }
    }

    protected fun PipelineContext<*>.finishAllAndProceed(): Nothing {
        runBlock { finishAll() }
    }

    companion object {
        val keyStoreFile = File("target/temp.jks")
        lateinit var keyStore: KeyStore
        lateinit var sslSocketFactory: SSLSocketFactory
        lateinit var hostConfig: (Int, Int) -> ApplicationHostConfig

        private fun generateCertificates(file: File, keyAlias: String = "mykey", password: String = "changeit"): KeyStore {
            val algorithm = "SHA1withRSA"
            val jks = KeyStore.getInstance("JKS")!!
            jks.load(null, null)

            val keyPairGenerator = KeyPairGenerator.getInstance("RSA")!!
            keyPairGenerator.initialize(1024)
            val keyPair = keyPairGenerator.genKeyPair()!!

            val certInfo = X509CertInfo()
            val from = Date()
            val to = LocalDateTime.now().plusDays(3).atZone(ZoneId.systemDefault())
            val certValidity = CertificateValidity(from, Date.from(to.toInstant()))

            val sn = BigInteger(64, SecureRandom())

            val owner = X500Name("cn=localhost, ou=Kotlin, o=JetBrains, c=RU")

            certInfo.set(X509CertInfo.VALIDITY, certValidity)
            certInfo.set(X509CertInfo.SERIAL_NUMBER, CertificateSerialNumber(sn))
            certInfo.set(X509CertInfo.SUBJECT, owner)
            certInfo.set(X509CertInfo.ISSUER, owner)
            certInfo.set(X509CertInfo.KEY, CertificateX509Key(keyPair.public))
            certInfo.set(X509CertInfo.VERSION, CertificateVersion(CertificateVersion.V3))
            certInfo.set(X509CertInfo.EXTENSIONS, CertificateExtensions().apply {
                set(SubjectAlternativeNameExtension.NAME, SubjectAlternativeNameExtension(GeneralNames().apply {
                    add(GeneralName(DNSName("localhost")))
                    add(GeneralName(IPAddressName("127.0.0.1")))
                }))
            })

            var algo = AlgorithmId(AlgorithmId.sha1WithRSAEncryption_oid)
            certInfo.set(X509CertInfo.ALGORITHM_ID, CertificateAlgorithmId(algo))

            var cert = X509CertImpl(certInfo)
            cert.sign(keyPair.private, algorithm)

            algo = cert.get(X509CertImpl.SIG_ALG) as AlgorithmId
            certInfo.set(CertificateAlgorithmId.NAME + "." + CertificateAlgorithmId.ALGORITHM, algo)
            certInfo.set("version", CertificateVersion(2))

            cert = X509CertImpl(certInfo)
            cert.sign(keyPair.private, algorithm)

            jks.setCertificateEntry(keyAlias, cert)
            jks.setKeyEntry(keyAlias, keyPair.private, password.toCharArray(), arrayOf(cert))

            file.parentFile.mkdirs()
            file.outputStream().use {
                jks.store(it, password.toCharArray())
            }

            return jks
        }

        @org.junit.BeforeClass
        @JvmStatic
        fun setupAll() {
            keyStore = generateCertificates(keyStoreFile)

            val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            tmf.init(keyStore)
            val ctx = SSLContext.getInstance("TLS")
            ctx.init(null, tmf.trustManagers, null)

            sslSocketFactory = ctx.socketFactory
            hostConfig = { port, sslPort ->
                applicationHostConfig {
                    connector {
                        this.port = port
                    }
                    sslConnector(keyStore, "mykey", { "changeit".toCharArray() }, { "changeit".toCharArray() }) {
                        this.port = sslPort
                        this.keyStorePath = keyStoreFile.absoluteFile
                    }
                }
            }
        }

        private fun waitForPort(port: Int) {
            do {
                Thread.sleep(50)
                try {
                    Socket("localhost", port).close()
                    break
                } catch (expected: IOException) {
                }
            } while (true)
        }
    }
}