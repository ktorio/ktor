package io.ktor.network.tls.cipher

import io.ktor.http.cio.internals.*
import io.ktor.network.tls.*
import kotlinx.io.core.*
import java.nio.*
import javax.crypto.*
import javax.crypto.spec.*

internal class CBCCipher(
    private val suite: CipherSuite,
    private val keyMaterial: ByteArray
) : TLSCipher {
    private val clientIV = keyMaterial.clientIV(suite)

    var inputCounter: Long = 0L
    var outputCounter: Long = 0L

    override fun encrypt(record: TLSRecord): TLSRecord {
        val cipher = Cipher.getInstance(suite.jdkCipherName)!!

        val key = keyMaterial.clientKey(suite)

        cipher.init(Cipher.ENCRYPT_MODE, key, IvParameterSpec(clientIV))

        val mac = Mac.getInstance("HMacSHA1")
        mac.init(keyMaterial.clientMacKey(suite))

        val aad = ByteArray(13).also {
            it.set(0, outputCounter)
            it[8] = record.type.code.toByte()
            it[9] = 3 // TLS 1.2
            it[10] = 3
            it.set(11, record.packet.remaining.toShort())
        }

        mac.update(aad)
        println("Material: ${keyMaterial.joinToString()}")

        val content = buildPacket {
            writePacket(record.packet.copy())

            val content = record.packet.readBytes()
            val macBytes = mac.doFinal(content)

            writeFully(macBytes)

            val paddingSize: Byte = (cipher.blockSize - (size + 1) % cipher.blockSize).toByte()

            repeat(paddingSize + 1) {
                writeByte(paddingSize)
            }
        }

        val packet = content.encrypted(cipher, outputCounter)
        outputCounter++

        return TLSRecord(record.type, packet = packet)
    }


    override fun decrypt(record: TLSRecord): TLSRecord {
        val cipher = Cipher.getInstance(suite.jdkCipherName)!!
        val packet = record.packet

        val key = keyMaterial.serverKey(suite)
        val serverIV = packet.readBytes(suite.fixedIvLength)
        cipher.init(Cipher.DECRYPT_MODE, key, IvParameterSpec(serverIV))

        val contentAndTag = packet.blockCipherLoop(cipher, writeIV = false)

        inputCounter++
        TODO()
    }
}

private fun ByteReadPacket.blockCipherLoop(cipher: Cipher, writeIV: Boolean = true): ByteReadPacket {
    val srcBuffer = DefaultByteBufferPool.borrow()
    var dstBuffer = CryptoBufferPool.borrow()
    var dstBufferFromPool = true

    try {
        return buildPacket {
            srcBuffer.clear()
            if (writeIV) writeFully(cipher.iv)

            while (true) {
                val rc = if (srcBuffer.hasRemaining()) readAvailable(srcBuffer) else 0
                srcBuffer.flip()

                if (!srcBuffer.hasRemaining() && (rc == -1 || this@blockCipherLoop.isEmpty)) break

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

            assert(!srcBuffer.hasRemaining()) { "Cipher loop completed too early: there are unprocessed bytes" }
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
