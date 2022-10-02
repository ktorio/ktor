/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.network.tls

import kotlinx.coroutines.*
import java.io.*
import java.security.*
import java.security.cert.*
import java.security.cert.Certificate
import javax.net.ssl.*

/**
 * [TLSConfig] builder.
 */
public actual class TLSConfigBuilder actual constructor(private val isClient: Boolean) {
    private var authenticationBuilder: TLSAuthenticationConfigBuilder? = null
    private var verificationBuilder: TLSVerificationConfigBuilder? = null

    /**
     * List of client certificate chains with private keys.
     */
    public val certificates: MutableList<CertificateAndKey> = mutableListOf()

    /**
     * [SecureRandom] to use in encryption.
     */
    public var random: SecureRandom? = null

    /**
     * Custom [X509TrustManager] to verify server authority.
     *
     * Use system by default.
     */
    public var trustManager: TrustManager? = null
        set(value) {
            value?.let {
                check(it is X509TrustManager) {
                    "Failed to set [trustManager]: $value. Only [X509TrustManager] supported."
                }
            }

            field = value
        }

    /**
     * List of allowed [CipherSuite]s.
     */
    public var cipherSuites: List<CipherSuite> = CIOCipherSuites.SupportedSuites

    /**
     * Custom server name for TLS server name extension.
     * See also: https://en.wikipedia.org/wiki/Server_Name_Indication
     */
    public actual var serverName: String? = null

    public actual fun authentication(
        privateKeyPassword: () -> CharArray,
        block: TLSAuthenticationConfigBuilder.() -> Unit
    ) {
        authenticationBuilder = TLSAuthenticationConfigBuilder(privateKeyPassword).apply(block)
    }

    public actual fun verification(
        block: TLSVerificationConfigBuilder.() -> Unit
    ) {
        verificationBuilder = TLSVerificationConfigBuilder().apply(block)
    }

    /**
     * Append config from [other] builder.
     */
    public actual fun takeFrom(other: TLSConfigBuilder) {
        certificates += other.certificates
        random = other.random
        cipherSuites = other.cipherSuites
        trustManager = other.trustManager
        serverName = other.serverName
        authenticationBuilder = other.authenticationBuilder
    }

    /**
     * Create [TLSConfig].
     */
    public actual fun build(): TLSConfig = TLSConfig(
        random = random ?: SecureRandom(),
        certificates = certificates,
        trustManager = trustManager as? X509TrustManager ?: findTrustManager(),
        cipherSuites = cipherSuites,
        isClient = isClient,
        serverName = serverName,
        authentication = authenticationBuilder?.build(),
        verification = verificationBuilder?.build()
    )
}

public actual class TLSAuthenticationConfigBuilder actual constructor(
    private val privateKeyPassword: () -> CharArray
) {
    private var keyStore: KeyStore? = null

    public actual fun pkcs12Certificate(
        certificatePath: String,
        certificatePassword: (() -> CharArray)?
    ) {
        pkcs12Certificate(File(certificatePath), certificatePassword)
    }

    public fun pkcs12Certificate(
        certificatePath: File,
        certificatePassword: (() -> CharArray)? = null
    ) {
        keyStore = KeyStore.getInstance("PKCS12").apply {
            val password = certificatePassword?.invoke()
            load(certificatePath.inputStream(), password)
            password?.fill('\u0000')
        }
    }

    public fun keyStore(keyStore: KeyStore) {
        this.keyStore = keyStore
    }

    public actual fun build(): TLSAuthenticationConfig {
        //TODO: what is the best place to put this as SSLContext doesn't check it
        keyStore?.apply {
            aliases().toList().forEach {
                (getCertificate(it) as? X509Certificate)?.checkValidity()
            }
        }

        return TLSAuthenticationConfig(
            KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply {
                val password = privateKeyPassword()
                init(keyStore, password)
                password.fill('\u0000')
            }
        )
    }
}

public actual class TLSVerificationConfigBuilder {
    private var keyStore: KeyStore? = null

    public actual fun pkcs12Certificate(
        certificatePath: String,
        certificatePassword: (() -> CharArray)?
    ) {
        pkcs12Certificate(File(certificatePath), certificatePassword)
    }

    public fun pkcs12Certificate(
        certificatePath: File,
        certificatePassword: (() -> CharArray)? = null
    ) {
        keyStore = KeyStore.getInstance("PKCS12").apply {
            val password = certificatePassword?.invoke()
            load(certificatePath.inputStream(), password)
            password?.fill('\u0000')
        }
    }

    public fun trustStore(keyStore: KeyStore) {
        this.keyStore = keyStore
    }

    public actual fun build(): TLSVerificationConfig {
        //TODO: what is the best place to put this as SSLContext doesn't check it
        keyStore?.apply {
            aliases().toList().forEach {
                (getCertificate(it) as? X509Certificate)?.checkValidity()
            }
        }

        return TLSVerificationConfig(
            TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply {
                init(keyStore)
            }
        )
    }
}

/**
 * Add client certificate chain to use.
 */
public fun TLSConfigBuilder.addCertificateChain(chain: Array<X509Certificate>, key: PrivateKey) {
    certificates += CertificateAndKey(chain, key)
}

/**
 * Add client certificates from [store] by using the certificate with specific [alias]
 * or all certificates, if [alias] is null.
 */
@JvmName("addKeyStoreNullablePassword")
public fun TLSConfigBuilder.addKeyStore(store: KeyStore, password: CharArray?, alias: String? = null) {
    val keyManagerAlgorithm = KeyManagerFactory.getDefaultAlgorithm()!!
    val keyManagerFactory = KeyManagerFactory.getInstance(keyManagerAlgorithm)!!

    keyManagerFactory.init(store, password)
    val managers = keyManagerFactory.keyManagers.filterIsInstance<X509KeyManager>()

    val aliases = alias?.let { listOf(it) } ?: store.aliases()!!.toList()
    loop@ for (certAlias in aliases) {
        val chain: Array<Certificate>? = store.getCertificateChain(certAlias)
        checkNotNull(chain) { "Fail to get the certificate chain for this alias: $certAlias" }

        val allX509 = chain.all { it is X509Certificate }
        check(allX509) { "Fail to add key store $store. Only X509 certificate format supported." }

        for (manager in managers) {
            val key = manager.getPrivateKey(certAlias) ?: continue

            val map = chain.map { it as X509Certificate }
            addCertificateChain(map.toTypedArray(), key)
            continue@loop
        }

        throw NoPrivateKeyException(certAlias, store)
    }
}

/**
 * Throws if failed to find [PrivateKey] for any alias in [KeyStore].
 */
@OptIn(ExperimentalCoroutinesApi::class)
public class NoPrivateKeyException(
    private val alias: String,
    private val store: KeyStore
) : IllegalStateException("Failed to find private key for alias $alias. Please check your key store: $store"),
    CopyableThrowable<NoPrivateKeyException> {

    override fun createCopy(): NoPrivateKeyException? = NoPrivateKeyException(alias, store).also {
        it.initCause(this)
    }
}

private fun findTrustManager(): X509TrustManager {
    val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())!!
    factory.init(null as KeyStore?)
    val manager = factory.trustManagers!!

    return manager.filterIsInstance<X509TrustManager>().first()
}
