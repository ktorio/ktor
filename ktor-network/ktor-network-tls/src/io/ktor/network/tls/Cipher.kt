package io.ktor.network.tls

import io.ktor.http.cio.internals.*
import io.ktor.network.util.*
import kotlinx.io.core.*
import java.security.*
import javax.crypto.*
import javax.crypto.spec.*


internal fun encryptCipher(
    suite: CipherSuite,
    keyMaterial: ByteArray,
    recordType: TLSRecordType,
    recordLength: Int, recordIv: Long, recordId: Long
): Cipher {
    val cipher = Cipher.getInstance(suite.jdkCipherName)

    val key = keyMaterial.clientKey(suite)
    val fixedIv = keyMaterial.clientIV(suite)
    val iv = fixedIv.copyOf(suite.ivLength)

    iv.set(suite.fixedIvLength, recordIv)

    // TODO non-gcm ciphers
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

internal fun decryptCipher(
    suite: CipherSuite,
    keyMaterial: ByteArray,
    recordType: TLSRecordType,
    recordLength: Int, recordIv: Long, recordId: Long
): Cipher {
    val cipher = Cipher.getInstance(suite.jdkCipherName)

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

internal fun ByteReadPacket.encrypted(cipher: Cipher, recordIv: Long): ByteReadPacket {
    val buffer = DefaultByteBufferPool.borrow()
    var encryptedPool = DefaultByteBufferPool
    var encrypted = encryptedPool.borrow()

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

                if (cipher.getOutputSize(buffer.remaining()) > encrypted.remaining()) {
                    encryptedPool.recycle(encrypted)
                    encryptedPool = DefaultDatagramByteBufferPool
                    encrypted = encryptedPool.borrow()
                }

                cipher.update(buffer, encrypted)
                encrypted.flip()
                writeFully(encrypted)
                buffer.compact()
            }

            writeFully(cipher.doFinal()) // TODO use encrypted buffer instead
        }
    } finally {
        DefaultByteBufferPool.recycle(buffer)
        encryptedPool.recycle(encrypted)
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

internal fun ByteArray.set(offset: Int, data: Long) {
    for (idx in 0..7) {
        this[idx + offset] = (data ushr (7 - idx) * 8).toByte()
    }
}

internal fun ByteArray.set(offset: Int, data: Int) {
    for (idx in 0..3) {
        this[idx + offset] = (data ushr (3 - idx) * 8).toByte()
    }
}

internal fun ByteArray.set(offset: Int, data: Short) {
    for (idx in 0..1) {
        this[idx + offset] = (data.toInt() ushr (1 - idx) * 8).toByte()
    }
}
