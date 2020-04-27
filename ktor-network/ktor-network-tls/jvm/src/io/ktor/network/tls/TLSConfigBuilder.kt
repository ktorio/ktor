/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.tls

import kotlinx.coroutines.*
import java.security.*
import java.security.cert.*
import javax.net.ssl.*

/**
 * [TLSConfig] builder.
 */
class TLSConfigBuilder {
    /**
     * List of client certificate chains with private keys.
     */
    val certificates: MutableList<CertificateAndKey> = mutableListOf()

    /**
     * [SecureRandom] to use in encryption.
     */
    var random: SecureRandom? = null

    /**
     * Custom [X509TrustManager] to verify server authority.
     *
     * Use system by default.
     */
    var trustManager: TrustManager? = null
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
    var cipherSuites: List<CipherSuite> = CIOCipherSuites.SupportedSuites

    /**
     * Custom server name for TLS server name extension.
     * See also: https://en.wikipedia.org/wiki/Server_Name_Indication
     */
    var serverName: String? = null

    /**
     * Create [TLSConfig].
     */
    fun build(): TLSConfig = TLSConfig(
        random ?: SecureRandom(),
        certificates, trustManager as? X509TrustManager ?: findTrustManager(),
        cipherSuites, serverName
    )
}

/**
 * Add client certificate chain to use.
 */
fun TLSConfigBuilder.addCertificateChain(chain: Array<X509Certificate>, key: PrivateKey) {
    certificates += CertificateAndKey(chain, key)
}

/**
 * Add client certificates from [store].
 */
fun TLSConfigBuilder.addKeyStore(store: KeyStore, password: CharArray) {
    val keyManagerAlgorithm = KeyManagerFactory.getDefaultAlgorithm()!!
    val keyManagerFactory = KeyManagerFactory.getInstance(keyManagerAlgorithm)!!

    keyManagerFactory.init(store, password)
    val managers = keyManagerFactory.keyManagers.filterIsInstance<X509KeyManager>()

    val aliases = store.aliases()!!
    loop@ for (alias in aliases) {
        val chain = store.getCertificateChain(alias)

        val allX509 = chain.all { it is X509Certificate }
        check(allX509) { "Fail to add key store $store. Only X509 certificate format supported." }

        for (manager in managers) {
            val key = manager.getPrivateKey(alias) ?: continue

            val map = chain.map { it as X509Certificate }
            addCertificateChain(map.toTypedArray(), key)
            continue@loop
        }

        throw NoPrivateKeyException(alias, store)
    }
}

/**
 * Throws if failed to find [PrivateKey] for any alias in [KeyStore].
 */
class NoPrivateKeyException(
    private val alias: String, private val store: KeyStore
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
