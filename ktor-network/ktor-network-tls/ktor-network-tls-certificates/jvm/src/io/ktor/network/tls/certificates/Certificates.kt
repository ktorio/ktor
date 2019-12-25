/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.tls.certificates

import io.ktor.network.tls.*
import io.ktor.utils.io.core.*
import java.io.*
import java.math.*
import java.net.*
import java.security.*
import java.security.cert.*
import java.text.*
import java.time.*
import java.util.*


/**
 * Generates simple self-signed certificate with [keyAlias] name, private key is encrypted with [keyPassword],
 * and a JKS keystore to hold it in [file] with [jksPassword].
 *
 * Only for testing purposes: NEVER use it for production!
 *
 * A generated certificate will have 3 days validity period and 1024-bits key strength.
 * Only localhost and 127.0.0.1 domains are valid with the certificate.
 */
fun generateCertificate(
    file: File,
    algorithm: String = "SHA1withRSA",
    keyAlias: String = "mykey",
    keyPassword: String = "changeit",
    jksPassword: String = keyPassword,
    keySizeInBits: Int = 1024
): KeyStore {
    val daysValid: Long = 3
    val keyStore = KeyStore.getInstance("JKS")!!
    keyStore.load(null, null)

    val keyPairGenerator = KeyPairGenerator.getInstance(keysGenerationAlgorithm(algorithm))!!
    keyPairGenerator.initialize(keySizeInBits)
    val keyPair = keyPairGenerator.genKeyPair()!!

    val id = Counterparty(
        country = "RU", organization = "JetBrains", organizationUnit = "Kotlin", commonName = "localhost"
    )

    val from = Date()
    val to = Date.from(LocalDateTime.now().plusDays(daysValid).atZone(ZoneId.systemDefault()).toInstant())


    val certificateBytes = buildPacket {
        writeCertificate(
            issuer = id, subject = id,
            keyPair = keyPair,
            algorithm = algorithm,
            from = from, to = to,
            domains = listOf("127.0.0.1", "localhost"),
            ipAddresses = listOf(Inet4Address.getByName("127.0.0.1"))
        )
    }.readBytes()

    val cert = CertificateFactory.getInstance("X.509").generateCertificate(certificateBytes.inputStream())

    cert.verify(keyPair.public)

    keyStore.setCertificateEntry(keyAlias, cert)
    keyStore.setKeyEntry(keyAlias, keyPair.private, keyPassword.toCharArray(), arrayOf(cert))

    file.parentFile?.mkdirs()
    file.outputStream().use {
        keyStore.store(it, jksPassword.toCharArray())
    }
    return keyStore
}


internal data class Counterparty(
    val country: String = "",
    val organization: String = "",
    val organizationUnit: String = "",
    val commonName: String = ""
)

internal fun BytePacketBuilder.writeX509Info(
    algorithm: String,
    issuer: Counterparty,
    subject: Counterparty,
    publicKey: PublicKey,
    from: Date,
    to: Date,
    domains: List<String>,
    ipAddresses: List<InetAddress>
) {

    val version = BigInteger(64, SecureRandom())

    writeDerSequence {
        writeVersion(2) // v3
        writeAsnInt(version) // certificate version

        writeAlgorithmIdentifier(algorithm)

        writeX509Counterparty(issuer)
        writeDerSequence {
            writeDerUTCTime(from)
            writeDerGeneralizedTime(to)
        }
        writeX509Counterparty(subject)

        writeFully(publicKey.encoded)

        writeByte(0xa3.toByte())
        val extensions = buildPacket {
            writeDerSequence {
                // subject alt name
                writeDerSequence {
                    writeDerObjectIdentifier(OID.SubjectAltName)
                    writeDerOctetString {
                        writeDerSequence {
                            for (domain in domains) {
                                writeX509Extension(2) {
                                    // DNSName
                                    writeFully(domain.toByteArray())
                                }
                            }
                            for (ip in ipAddresses) {
                                writeX509Extension(7) {
                                    // IP address
                                    writeFully(ip.address)
                                }
                            }
                        }
                    }
                }
            }
        }

        writeDerLength(extensions.remaining.toInt())
        writePacket(extensions)
    }
}

private fun BytePacketBuilder.writeAlgorithmIdentifier(algorithm: String) {
    writeDerSequence {
        val oid = OID.fromAlgorithm(algorithm)
        writeDerObjectIdentifier(oid)
        writeDerNull()
    }
}

private fun BytePacketBuilder.writeX509Extension(id: Int, builder: BytePacketBuilder.() -> Unit) {
    writeByte((0x80 or id).toByte())
    val packet = buildPacket { builder() }
    writeDerLength(packet.remaining.toInt())
    writePacket(packet)
}

private fun BytePacketBuilder.writeX509NamePart(id: OID, value: String) {
    writeDerSet {
        writeDerSequence {
            writeDerObjectIdentifier(id)
            writeDerUTF8String(value)
        }
    }
}

private fun BytePacketBuilder.writeX509Counterparty(counterparty: Counterparty) {
    writeDerSequence {
        if (counterparty.country.isNotEmpty()) {
            writeX509NamePart(OID.CountryName, counterparty.country)
        }
        if (counterparty.organization.isNotEmpty()) {
            writeX509NamePart(OID.OrganizationName, counterparty.organization)
        }
        if (counterparty.organizationUnit.isNotEmpty()) {
            writeX509NamePart(OID.OrganizationalUnitName, counterparty.organizationUnit)
        }
        if (counterparty.commonName.isNotEmpty()) {
            writeX509NamePart(OID.CommonName, counterparty.commonName)
        }
    }
}

internal fun BytePacketBuilder.writeCertificate(
    issuer: Counterparty,
    subject: Counterparty,
    keyPair: KeyPair,
    algorithm: String,
    from: Date, to: Date,
    domains: List<String>,
    ipAddresses: List<InetAddress>
) {
    require(to.after(from))

    val certInfo = buildPacket {
        writeX509Info(algorithm, issuer, subject, keyPair.public, from, to, domains, ipAddresses)
    }

    val certInfoBytes = certInfo.readBytes()
    val signature = Signature.getInstance(algorithm)
    signature.initSign(keyPair.private)
    signature.update(certInfoBytes)
    val signed = signature.sign()

    writeDerSequence {
        writeFully(certInfoBytes)
        writeDerSequence {
            writeDerObjectIdentifier(OID.fromAlgorithm(algorithm))
            writeDerNull()
        }
        writeDerBitString(signed)
    }
}

private fun BytePacketBuilder.writeVersion(v: Int = 2) {
    writeDerType(2, 0, false)
    val encoded = buildPacket {
        writeAsnInt(v)
    }
    writeDerLength(encoded.remaining.toInt())
    writePacket(encoded)
}

private fun BytePacketBuilder.writeDerOctetString(block: BytePacketBuilder.() -> Unit) {
    val sub = buildPacket { block() }

    writeDerType(0, 4, true)
    writeDerLength(sub.remaining.toInt())
    writePacket(sub)
}

private fun BytePacketBuilder.writeDerBitString(block: BytePacketBuilder.() -> Unit) {
    val sub = buildPacket { block() }

    writeDerType(0, 3, true)
    writeDerLength(sub.remaining.toInt() + 1)
    writeByte(0)
    writePacket(sub)
}

private fun BytePacketBuilder.writeDerBitString(array: ByteArray, unused: Int = 0) {
    require(unused in 0..7)

    writeDerType(0, 3, true)
    writeDerLength(array.size + 1)
    writeByte(unused.toByte())
    writeFully(array)
}

private fun BytePacketBuilder.writeDerUTCTime(date: Date) {
    writeDerUTF8String(SimpleDateFormat("yyMMddHHmmss'Z'").apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }.format(date), 0x17)
}

private fun BytePacketBuilder.writeDerGeneralizedTime(date: Date) {
    writeDerUTF8String(
        SimpleDateFormat("yyyyMMddHHmmss'Z'").apply { timeZone = TimeZone.getTimeZone("UTC") }.format(
            date
        ), 0x18
    )
}

private fun BytePacketBuilder.writeDerUTF8String(s: String, type: Int = 0x0c) {
    val sub = buildPacket {
        writeText(s)
    }

    writeDerType(0, type, true)
    writeDerLength(sub.remaining.toInt())
    writePacket(sub)
}

private fun BytePacketBuilder.writeDerNull() {
    writeShort(0x0500)
}

private fun BytePacketBuilder.writeDerSequence(block: BytePacketBuilder.() -> Unit) {
    val sub = buildPacket { block() }

    writeDerType(0, 0x10, false)
    writeDerLength(sub.remaining.toInt())
    writePacket(sub)
}

private fun BytePacketBuilder.writeDerSet(block: BytePacketBuilder.() -> Unit) {
    val sub = buildPacket { block() }

    writeDerType(0, 0x11, false)
    writeDerLength(sub.remaining.toInt())
    writePacket(sub)
}

private fun BytePacketBuilder.writeDerObjectIdentifier(identifier: OID) {
    writeDerObjectIdentifier(identifier.asArray)
}

private fun BytePacketBuilder.writeDerObjectIdentifier(identifier: IntArray) {
    require(identifier.size >= 2)
    require(identifier[0] in 0..2)
    require(identifier[0] == 2 || identifier[1] in 0..39)

    val sub = buildPacket {
        writeDerInt(identifier[0] * 40 + identifier[1])

        for (i in 2..identifier.lastIndex) {
            writeDerInt(identifier[i])
        }
    }

    writeDerType(0, 6, true)
    writeDerLength(sub.remaining.toInt())
    writePacket(sub)
}

private fun BytePacketBuilder.writeAsnInt(value: BigInteger) {
    writeDerType(0, 2, true)

    val encoded = value.toByteArray()
    writeDerLength(encoded.size)
    writeFully(encoded)
}

private fun BytePacketBuilder.writeAsnInt(value: Int) {
    writeDerType(0, 2, true)

    val encoded = buildPacket {
        var skip = true

        for (idx in 0..3) {
            val part = (value ushr ((4 - idx - 1) * 8) and 0xff)
            if (part == 0 && skip) {
                continue
            } else {
                skip = false
            }

            writeByte(part.toByte())
        }
    }
    writeDerLength(encoded.remaining.toInt())
    writePacket(encoded)
}

private fun BytePacketBuilder.writeDerLength(length: Int) {
    require(length >= 0)

    when {
        length <= 0x7f -> writeByte(length.toByte())
        length <= 0xff -> {
            writeByte(0x81.toByte())
            writeByte(length.toByte())
        }
        length <= 0xffff -> {
            writeByte(0x82.toByte())
            writeByte((length ushr 8).toByte())
            writeByte(length.toByte())
        }
        length <= 0xffffff -> {
            writeByte(0x83.toByte())
            writeByte((length ushr 16).toByte())
            writeByte(((length ushr 8) and 0xff).toByte())
            writeByte(length.toByte())
        }
        else -> {
            writeByte(0x84.toByte())
            writeByte((length ushr 24).toByte())
            writeByte(((length ushr 16) and 0xff).toByte())
            writeByte(((length ushr 8) and 0xff).toByte())
            writeByte(length.toByte())
        }
    }
}

private fun BytePacketBuilder.writeDerType(kind: Int, typeIdentifier: Int, simpleType: Boolean) {
    require(kind in 0..3)
    require(typeIdentifier >= 0)

    if (typeIdentifier in 0..30) {
        val singleByte = (kind shl 6) or typeIdentifier or (if (simpleType) 0 else 0x20)
        val byteValue = singleByte.toByte()
        writeByte(byteValue)
    } else {
        val firstByte = (kind shl 6) or 0x1f or (if (simpleType) 0 else 0x20)
        writeByte(firstByte.toByte())
        writeDerInt(typeIdentifier)
    }
}

private fun Int.derLength(): Int {
    require(this >= 0)
    if (this == 0) return 0

    var mask = 0x7f
    var byteCount = 1

    while (true) {
        if (this and mask == this) break
        mask = mask or (mask shl 7)
        byteCount++
    }

    return byteCount
}

private fun BytePacketBuilder.writeDerInt(value: Int) {
    require(value >= 0)

    val byteCount = value.derLength()

    repeat(byteCount) { idx ->
        val part = (value shr ((byteCount - idx - 1) * 7) and 0x7f)
        if (idx == byteCount - 1) {
            writeByte(part.toByte())
        } else {
            writeByte((part or 0x80).toByte())
        }
    }
}

//private fun ByteArray.hexdump(): String = buildString(size * 4) {
//    for (idx in 0 until size) {
//        val s = (this@hexdump[idx].toInt() and 0xff).toString(16)
//        if (s.length == 1) {
//            append('0')
//        }
//        append(s)
//
//        when (idx and 0x0f) { // col
//            15 -> appendln()
//            7 -> append("  ")
//            else -> append(' ')
//        }
//    }
//}
