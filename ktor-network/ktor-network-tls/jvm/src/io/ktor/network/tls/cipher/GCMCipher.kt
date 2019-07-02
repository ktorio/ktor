/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.tls.cipher

import io.ktor.network.tls.*
import io.ktor.utils.io.core.*
import javax.crypto.*
import javax.crypto.spec.*

internal class GCMCipher(
    private val suite: CipherSuite,
    private val keyMaterial: ByteArray
) : TLSCipher {
    private var inputCounter: Long = 0L
    private var outputCounter: Long = 0L

    override fun encrypt(record: TLSRecord): TLSRecord {
        val cipher = gcmEncryptCipher(
            suite, keyMaterial, record.type, record.packet.remaining.toInt(),
            outputCounter, outputCounter
        )

        val packetId = outputCounter
        val packet = record.packet.cipherLoop(cipher) {
            writeLong(packetId)
        }

        outputCounter++

        return TLSRecord(record.type, packet = packet)
    }

    override fun decrypt(record: TLSRecord): TLSRecord {
        val packet = record.packet
        val packetSize = packet.remaining
        val recordIv = packet.readLong()

        val cipher = gcmDecryptCipher(
            suite, keyMaterial, record.type, packetSize.toInt(), recordIv, inputCounter++
        )

        return TLSRecord(record.type, record.version, packet.cipherLoop(cipher))
    }
}

private fun gcmEncryptCipher(
    suite: CipherSuite,
    keyMaterial: ByteArray,
    recordType: TLSRecordType,
    recordLength: Int, recordIv: Long, recordId: Long
): Cipher {
    val cipher = Cipher.getInstance(suite.jdkCipherName)!!

    val key = keyMaterial.clientKey(suite)
    val fixedIv = keyMaterial.clientIV(suite)
    val iv = fixedIv.copyOf(suite.ivLength)

    iv.set(suite.fixedIvLength, recordIv)

    val gcmSpec = GCMParameterSpec(suite.cipherTagSizeInBytes * 8, iv)
    cipher.init(Cipher.ENCRYPT_MODE, key, gcmSpec)

    val aad = ByteArray(13).also {
        it.set(0, recordId)
        it[8] = recordType.code.toByte()
        it[9] = 3 // TLS 1.2
        it[10] = 3
        it.set(11, recordLength.toShort())
    }

    cipher.updateAAD(aad)
    return cipher
}

private fun gcmDecryptCipher(
    suite: CipherSuite,
    keyMaterial: ByteArray,
    recordType: TLSRecordType,
    recordLength: Int, recordIv: Long, recordId: Long
): Cipher {
    val cipher = Cipher.getInstance(suite.jdkCipherName)!!

    val key = keyMaterial.serverKey(suite)
    val fixedIv = keyMaterial.serverIV(suite)
    val iv = fixedIv.copyOf(suite.ivLength)

    iv.set(suite.fixedIvLength, recordIv)

    // TODO non-gcm ciphers
    val gcmSpec = GCMParameterSpec(suite.cipherTagSizeInBytes * 8, iv)

    cipher.init(Cipher.DECRYPT_MODE, key, gcmSpec)

    val contentSize = recordLength - (suite.ivLength - suite.fixedIvLength) - suite.cipherTagSizeInBytes
    check(contentSize < 0x10000) { "Content size should fit in 2 bytes, actual: $contentSize" }

    val aad = ByteArray(13).also {
        it.set(0, recordId)
        it[8] = recordType.code.toByte()
        it[9] = 3 // TLS 1.2
        it[10] = 3
        it.set(11, contentSize.toShort())
    }

    cipher.updateAAD(aad)
    return cipher
}
