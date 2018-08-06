package io.ktor.network.tls.certificates

import io.ktor.network.tls.*
import io.ktor.network.tls.extensions.*
import kotlinx.io.core.*
import java.io.*
import java.net.*
import java.security.*
import java.security.cert.*
import java.security.cert.Certificate
import java.time.*
import java.util.*

data class CertificateInfo(val certificate: Certificate, val keys: KeyPair, val password: String)

class CertificateBuilder {
    lateinit var hash: HashAlgorithm
    lateinit var sign: SignatureAlgorithm
    lateinit var password: String

    var daysValid: Long = 3
    var keySizeInBits: Int = 1024

    fun build(): CertificateInfo {
        val algorithm = HashAndSign(hash, sign)
        val keys = KeyPairGenerator.getInstance(keysGenerationAlgorithm(algorithm.name))!!.apply {
            initialize(keySizeInBits)
        }.genKeyPair()!!

        val id = Counterparty(
            country = "RU", organization = "JetBrains", organizationUnit = "Kotlin", commonName = "localhost"
        )

        val from = Date()
        val to = Date.from(LocalDateTime.now().plusDays(daysValid).atZone(ZoneId.systemDefault()).toInstant())

        val certificateBytes = buildPacket {
            writeCertificate(
                issuer = id, subject = id,
                keyPair = keys,
                algorithm = algorithm.name,
                from = from, to = to,
                domains = listOf("localhost"),
                ipAddresses = listOf(Inet4Address.getByName("127.0.0.1"))
            )
        }.readBytes()

        val cert = CertificateFactory.getInstance("X.509").generateCertificate(certificateBytes.inputStream())
        cert.verify(keys.public)
        return CertificateInfo(cert, keys, password)
    }
}

class KeyStoreBuilder {
    private val certificates = mutableMapOf<String, CertificateInfo>()

    fun certificate(alias: String, block: CertificateBuilder.() -> Unit) {
        certificates[alias] = CertificateBuilder().apply(block).build()
    }

    fun build(): KeyStore {
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

fun buildKeyStore(block: KeyStoreBuilder.() -> Unit): KeyStore = KeyStoreBuilder().apply(block).build()

fun KeyStore.saveToFile(output: File, password: String) {
    output.parentFile?.mkdirs()

    output.outputStream().use {
        store(it, password.toCharArray())
    }
}
