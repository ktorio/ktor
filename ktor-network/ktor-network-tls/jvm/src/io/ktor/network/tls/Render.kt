/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.tls

import io.ktor.network.tls.extensions.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import java.security.*
import java.security.cert.*
import java.security.interfaces.*
import java.security.spec.*
import javax.crypto.*

internal suspend fun ByteWriteChannel.writeRecord(record: TLSRecord) = with(record) {
    writeByte(type.code.toByte())
    writeByte((version.code shr 8).toByte())
    writeByte(version.code.toByte())
    writeShort(packet.remaining.toShort())
    writePacket(packet)
    flush()
}

internal fun BytePacketBuilder.writeTLSHandshakeType(type: TLSHandshakeType, length: Int) {
    if (length > 0xffffff) throw TLSException("TLS handshake size limit exceeded: $length")
    val v = (type.code shl 24) or length
    writeInt(v)
}

internal fun BytePacketBuilder.writeTLSCertificates(certificates: Array<X509Certificate>) {
    val chain = buildPacket {
        for (certificate in certificates) {
            val certificateBytes = certificate.encoded!!
            writeTripleByteLength(certificateBytes.size)
            writeFully(certificateBytes)
        }
    }

    writeTripleByteLength(chain.remaining.toInt())
    writePacket(chain)
}

internal fun BytePacketBuilder.writeEncryptedPreMasterSecret(
    preSecret: ByteArray,
    publicKey: PublicKey,
    random: SecureRandom
) {
    require(preSecret.size == 48)

    val rsaCipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")!!
    rsaCipher.init(Cipher.ENCRYPT_MODE, publicKey, random)
    val encryptedSecret = rsaCipher.doFinal(preSecret)

    if (encryptedSecret.size > 0xffff) throw TLSException("Encrypted premaster secret is too long")

    writeShort(encryptedSecret.size.toShort())
    writeFully(encryptedSecret)
}

internal fun finished(digest: ByteArray, secretKey: SecretKey) = buildPacket {
    val prf = PRF(secretKey, CLIENT_FINISHED_LABEL, digest, 12)
    writeFully(prf)
}

internal fun BytePacketBuilder.writePublicKeyUncompressed(key: PublicKey) = when (key) {
    is ECPublicKey -> {
        val fieldSize = key.params.curve.field.fieldSize
        writeECPoint(key.w, fieldSize)
    }
    else -> throw TLSException("Unsupported public key type: $key")
}

internal fun buildSignatureAlgorithmsExtension(
    algorithms: List<HashAndSign> = SupportedSignatureAlgorithms
): ByteReadPacket = buildPacket {
    writeShort(TLSExtensionType.SIGNATURE_ALGORITHMS.code) // signature_algorithms extension

    val size = algorithms.size
    writeShort((2 + size * 2).toShort()) // length in bytes
    writeShort((size * 2).toShort()) // length in bytes

    algorithms.forEach {
        writeByte(it.hash.code)
        writeByte(it.sign.code)
    }
}

internal fun BytePacketBuilder.writeTripleByteLength(value: Int) {
    val high = (value ushr 16) and 0xff
    val low = value and 0xffff
    writeByte(high.toByte())
    writeShort(low.toShort())
}
