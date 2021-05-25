/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.tls.certificates

import io.ktor.network.tls.*
import io.ktor.network.tls.extensions.*
import io.ktor.util.*
import io.ktor.utils.io.core.*
import java.io.*
import java.net.*
import java.security.*
import java.security.cert.*
import java.security.cert.Certificate
import java.time.*
import java.util.*

internal data class CertificateInfo(val certificate: Certificate, val keys: KeyPair, val password: String)

/**
 * Builder for certificate
 */
public class CertificateBuilder internal constructor() {
    /**
     * Certificate hash algorithm (required)
     */
    public lateinit var hash: HashAlgorithm

    /**
     * Certificate signature algorithm (required)
     */
    public lateinit var sign: SignatureAlgorithm

    /**
     * Certificate password
     */
    public lateinit var password: String

    /**
     * Number of days the certificate is valid
     */
    public var daysValid: Long = 3

    /**
     * Certificate key size in bits
     */
    public var keySizeInBits: Int = 1024

    internal fun build(): CertificateInfo {
        val algorithm = HashAndSign(hash, sign)
        val keys = KeyPairGenerator.getInstance(keysGenerationAlgorithm(algorithm.name))!!.apply {
            initialize(keySizeInBits)
        }.genKeyPair()!!

        val id = Counterparty(
            country = "RU",
            organization = "JetBrains",
            organizationUnit = "Kotlin",
            commonName = "localhost"
        )

        val from = Date()
        val to = Date.from(LocalDateTime.now().plusDays(daysValid).atZone(ZoneId.systemDefault()).toInstant())

        val certificateBytes = buildPacket {
            writeCertificate(
                issuer = id,
                subject = id,
                keyPair = keys,
                algorithm = algorithm.name,
                from = from,
                to = to,
                domains = listOf("localhost"),
                ipAddresses = listOf(Inet4Address.getByName("127.0.0.1"))
            )
        }.readBytes()

        val cert = CertificateFactory.getInstance("X.509").generateCertificate(certificateBytes.inputStream())
        cert.verify(keys.public)
        return CertificateInfo(cert, keys, password)
    }
}

/**
 * Builder for key store
 */
public class KeyStoreBuilder internal constructor() {
    private val certificates = mutableMapOf<String, CertificateInfo>()

    /**
     * Generate a certificate and append to the key store.
     * If there is a certificate with the same [alias] then it will be replaced
     */
    public fun certificate(alias: String, block: CertificateBuilder.() -> Unit) {
        certificates[alias] = CertificateBuilder().apply(block).build()
    }

    internal fun build(): KeyStore {
        val store = KeyStore.getInstance("JKS")!!
        store.load(null, null)

        certificates.forEach { (alias, info) ->
            val (certificate, keys, password) = info
            store.setCertificateEntry(alias, certificate)
            store.setKeyEntry(alias, keys.private, password.toCharArray(), arrayOf(certificate))
        }

        return store
    }
}

/**
 * Create a keystore and configure it in [block] function
 */
public fun buildKeyStore(block: KeyStoreBuilder.() -> Unit): KeyStore = KeyStoreBuilder().apply(block).build()

/**
 * Save [KeyStore] to [output] file with the specified [password]
 */
public fun KeyStore.saveToFile(output: File, password: String) {
    output.parentFile?.mkdirs()

    output.outputStream().use {
        store(it, password.toCharArray())
    }
}
