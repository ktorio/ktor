package io.ktor.network.tls

import io.ktor.http.cio.internals.*
import kotlinx.io.core.*
import kotlinx.io.pool.*
import java.nio.*
import javax.crypto.*
import javax.crypto.spec.*

private val CryptoBufferPool: ObjectPool<ByteBuffer> = object : DefaultPool<ByteBuffer>(128) {
    override fun produceInstance(): ByteBuffer = ByteBuffer.allocate(65536)
    override fun clearInstance(instance: ByteBuffer): ByteBuffer = instance.apply { clear() }
}

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
    return cipherLoop(cipher, recordIv, true)
}

internal fun ByteReadPacket.decrypted(cipher: Cipher): ByteReadPacket {
    return cipherLoop(cipher, 0, false)
}

private fun ByteReadPacket.cipherLoop(cipher: Cipher, recordIv: Long, writeRecordIv: Boolean): ByteReadPacket {
    val srcBuffer = DefaultByteBufferPool.borrow()
    var dstBuffer = CryptoBufferPool.borrow()
    var dstBufferFromPool = true

    try {
        return buildPacket {
            srcBuffer.clear()
            if (writeRecordIv) {
                writeLong(recordIv)
            }

            while (true) {
                val rc = if (srcBuffer.hasRemaining()) readAvailable(srcBuffer) else 0
                srcBuffer.flip()

                if (!srcBuffer.hasRemaining() && (rc == -1 || this@cipherLoop.isEmpty)) break

                dstBuffer.clear()

                if (cipher.getOutputSize(srcBuffer.remaining()) > dstBuffer.remaining()) {
                    if (dstBufferFromPool) {
                        CryptoBufferPool.recycle(dstBuffer)
                    }
                    dstBuffer = ByteBuffer.allocate(cipher.getOutputSize(srcBuffer.remaining()))
                    dstBufferFromPool = false
                }

                cipher.update(srcBuffer, dstBuffer)
                dstBuffer.flip()
                writeFully(dstBuffer)
                srcBuffer.compact()
            }

            assert(!srcBuffer.hasRemaining()) { "Cipher loop completed too early: there are unprocessed bytes"}
            assert(!dstBuffer.hasRemaining()) { "Not all bytes were appended to the packet" }

            val requiredBufferSize = cipher.getOutputSize(0)
            if (requiredBufferSize == 0) return@buildPacket
            if (requiredBufferSize > dstBuffer.capacity()) {
                writeFully(cipher.doFinal())
                return@buildPacket
            }

            dstBuffer.clear()
            cipher.doFinal(EmptyByteBuffer, dstBuffer)
            dstBuffer.flip()

            if (!dstBuffer.hasRemaining()) { // workaround JDK bug
                writeFully(cipher.doFinal())
                return@buildPacket
            }

            writeFully(dstBuffer)
        }
    } finally {
        DefaultByteBufferPool.recycle(srcBuffer)
        if (dstBufferFromPool) {
            CryptoBufferPool.recycle(dstBuffer)
        }
    }
}

private fun ByteArray.set(offset: Int, data: Long) {
    for (idx in 0..7) {
        this[idx + offset] = (data ushr (7 - idx) * 8).toByte()
    }
}

private fun ByteArray.set(offset: Int, data: Short) {
    for (idx in 0..1) {
        this[idx + offset] = (data.toInt() ushr (1 - idx) * 8).toByte()
    }
}

private val EmptyByteBuffer: ByteBuffer = ByteBuffer.allocate(0)
