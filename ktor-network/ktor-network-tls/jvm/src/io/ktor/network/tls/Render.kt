/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.tls

import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import java.security.*
import java.security.cert.*
import java.security.interfaces.*
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
    if (length > 0xffffff) throw TLSValidationException("TLS handshake size limit exceeded: $length")
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

    if (encryptedSecret.size > 0xffff) throw TLSValidationException("Encrypted premaster secret is too long")

    writeShort(encryptedSecret.size.toShort())
    writeFully(encryptedSecret)
}

internal fun BytePacketBuilder.writePublicKeyUncompressed(key: PublicKey) = when (key) {
    is ECPublicKey -> {
        val fieldSize = key.params.curve.field.fieldSize
        writeECPoint(key.w, fieldSize)
    }
    else -> throw TLSUnsupportedException("Unsupported public key type: $key")
}

internal fun BytePacketBuilder.writeTripleByteLength(value: Int) {
    val high = (value ushr 16) and 0xff
    val low = value and 0xffff
    writeByte(high.toByte())
    writeShort(low.toShort())
}
