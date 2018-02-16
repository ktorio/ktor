package io.ktor.network.tls

import io.ktor.http.cio.internals.*
import io.ktor.network.util.*
import kotlinx.io.core.*
import javax.crypto.*
import javax.crypto.spec.*


internal fun encryptCipher(suite: CipherSuite, keyMaterial: ByteArray, recordType: TLSRecordType, recordLength: Int, recordIv: Long, seq: Long): Cipher {
    val cipher = Cipher.getInstance(suite.jdkCipherName)

    val key = keyMaterial.clientKey(suite)
    val fixedIv = keyMaterial.clientIV(suite)
    val iv = fixedIv.copyOf(suite.ivLength)

    var s = recordIv
    for (idx in suite.ivLength - 1 downTo suite.fixedIvLength) {
        iv[idx] = (s and 0xff).toByte()
        s = s ushr 8
    }

    // TODO non-gcm ciphers
    val gcmSpec = GCMParameterSpec(suite.cipherTagSizeInBytes * 8, iv)

    cipher.init(Cipher.ENCRYPT_MODE, key, gcmSpec)

    val aad = ByteArray(13)
    s = seq
    for (idx in 7 downTo 0) {
        aad[idx] = (s and 0xff).toByte()
        s = s ushr 8
    }
    aad[9] = 3 // TLS 1.2
    aad[10] = 3

    aad[8] = recordType.code.toByte()
    aad[11] = (recordLength shr 8).toByte()
    aad[12] = (recordLength and 0xff).toByte()

    cipher.updateAAD(aad)

    return cipher
}

internal fun decryptCipher(suite: CipherSuite, keyMaterial: ByteArray, recordType: TLSRecordType, recordLength: Int, recordIv: Long, seq: Long): Cipher {
    val cipher = Cipher.getInstance(suite.jdkCipherName)

    val key = keyMaterial.serverKey(suite)
    val fixedIv = keyMaterial.serverIV(suite)
    val iv = fixedIv.copyOf(suite.ivLength)

    var s = recordIv
    for (idx in suite.ivLength - 1 downTo suite.fixedIvLength) {
        iv[idx] = (s and 0xff).toByte()
        s = s ushr 8
    }

    // TODO non-gcm ciphers
    val gcmSpec = GCMParameterSpec(suite.cipherTagSizeInBytes * 8, iv)

    cipher.init(Cipher.DECRYPT_MODE, key, gcmSpec)

    val contentSize = recordLength - (suite.ivLength - suite.fixedIvLength) - suite.cipherTagSizeInBytes
    val aad = ByteArray(13)
    s = seq
    for (idx in 7 downTo 0) {
        aad[idx] = (s and 0xff).toByte()
        s = s ushr 8
    }

    aad[9] = 3 // TLS 1.2
    aad[10] = 3

    aad[8] = recordType.code.toByte()
    aad[11] = (contentSize shr 8).toByte()
    aad[12] = (contentSize and 0xff).toByte()

    cipher.updateAAD(aad)

    return cipher
}


internal fun ByteReadPacket.encrypted(cipher: Cipher, recordIv: Long): ByteReadPacket {
    val buffer = DefaultByteBufferPool.borrow()
    val encrypted = DefaultByteBufferPool.borrow()
    try {
        return buildPacket {
            buffer.clear()

            writeLong(recordIv)

            while (true) {
                val rc = if (buffer.hasRemaining()) readAvailable(buffer) else 0
                if (rc == -1) break
                buffer.flip()

                if (!buffer.hasRemaining() && isEmpty) break

                encrypted.clear()
                cipher.update(buffer, encrypted)
                encrypted.flip()
                writeFully(encrypted)
                buffer.compact()
            }

            writeFully(cipher.doFinal()) // TODO use encrypted buffer instead
        }
    } finally {
        DefaultByteBufferPool.recycle(buffer)
        DefaultByteBufferPool.recycle(encrypted)
    }
}

internal fun ByteReadPacket.decrypted(cipher: Cipher): ByteReadPacket {
    val buffer = DefaultByteBufferPool.borrow()
    var decrypted = DefaultByteBufferPool.borrow()
    var decryptedPool = DefaultByteBufferPool

    try {
        return buildPacket {
            buffer.clear()

            while (true) {
                val rc = if (buffer.hasRemaining()) readAvailable(buffer) else 0
                if (rc == -1) break
                buffer.flip()

                if (!buffer.hasRemaining() && isEmpty) break

                decrypted.clear()

                if (cipher.getOutputSize(buffer.remaining()) > decrypted.remaining()) {
                    if (buffer.capacity() < 65536) {
                        decryptedPool.recycle(decrypted)
                        decryptedPool = DefaultDatagramByteBufferPool
                        decrypted = decryptedPool.borrow()
                        decrypted.clear()
                    }
                }

                cipher.update(buffer, decrypted)
                decrypted.flip()
                writeFully(decrypted)
                buffer.compact()
            }

            writeFully(cipher.doFinal()) // TODO use decrypted buffer instead
        }
    } finally {
        DefaultByteBufferPool.recycle(buffer)
        decryptedPool.recycle(decrypted)
    }
}