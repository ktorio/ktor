/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.tls.cipher

import io.ktor.network.tls.*
import io.ktor.utils.io.core.*
import javax.crypto.*
import javax.crypto.spec.*

private class GCMClientCipher(
    suite: CipherSuite,
    keyMaterial: ByteArray,
) : GCMCipher(suite, keyMaterial) {
    override fun encryptKey(): SecretKeySpec = keyMaterial.clientKey(suite)
    override fun encryptIV(): ByteArray = keyMaterial.clientIV(suite)
    override fun decryptKey(): SecretKeySpec = keyMaterial.serverKey(suite)
    override fun decryptIV(): ByteArray = keyMaterial.serverIV(suite)
}

private class GCMServerCipher(
    suite: CipherSuite,
    keyMaterial: ByteArray,
) : GCMCipher(suite, keyMaterial) {
    override fun encryptKey(): SecretKeySpec = keyMaterial.serverKey(suite)
    override fun encryptIV(): ByteArray = keyMaterial.serverIV(suite)
    override fun decryptKey(): SecretKeySpec = keyMaterial.clientKey(suite)
    override fun decryptIV(): ByteArray = keyMaterial.clientIV(suite)
}

internal abstract class GCMCipher(
    protected val suite: CipherSuite,
    protected val keyMaterial: ByteArray,
) : TLSCipher {
    companion object {
        fun create(suite: CipherSuite, keyMaterial: ByteArray, role: NetworkRole) =
            when (role) {
                NetworkRole.SERVER -> GCMServerCipher(suite, keyMaterial)
                NetworkRole.CLIENT -> GCMClientCipher(suite, keyMaterial)
            }
    }
    private var inputCounter: Long = 0L
    private var outputCounter: Long = 0L

    abstract fun encryptKey(): SecretKeySpec
    abstract fun encryptIV(): ByteArray
    abstract fun decryptKey(): SecretKeySpec
    abstract fun decryptIV(): ByteArray

    override fun encrypt(record: TLSRecord): TLSRecord {
        val cipher = gcmEncryptCipher(
            record.type,
            record.packet.remaining.toInt(),
            outputCounter,
            outputCounter
        )

        val packetId = outputCounter++
        val encrypted = record.packet.cipherLoop(cipher) {
            writeLong(packetId)
        }

        return TLSRecord(record.type, record.version, encrypted)
    }

    override fun decrypt(record: TLSRecord): TLSRecord {
        val packet = record.packet
        val packetSize = packet.remaining
        val recordIv = packet.readLong()

        val cipher = gcmDecryptCipher(
            record.type,
            packetSize.toInt(),
            recordIv,
            inputCounter++
        )

        val decrypted = packet.cipherLoop(cipher)

        return TLSRecord(record.type, record.version, decrypted)
    }

    private fun gcmEncryptCipher(
        recordType: TLSRecordType,
        recordLength: Int,
        recordIv: Long,
        recordId: Long
    ): Cipher {
        val cipher = Cipher.getInstance(suite.jdkCipherName)!!

        val key = encryptKey()
        val fixedIv = encryptIV()
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
        recordType: TLSRecordType,
        recordLength: Int,
        recordIv: Long,
        recordId: Long
    ): Cipher {
        val cipher = Cipher.getInstance(suite.jdkCipherName)!!

        val key = decryptKey()
        val fixedIv = decryptIV()
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
}
