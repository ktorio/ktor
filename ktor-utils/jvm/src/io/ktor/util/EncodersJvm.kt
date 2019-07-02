/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util

import io.ktor.util.cio.*
import kotlinx.coroutines.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import java.nio.*
import java.util.zip.*

private const val GZIP_HEADER_SIZE: Int = 10

/**
 * Implementation of Deflate [Encoder].
 */
val Deflate: Encoder = object : Encoder {
    override fun CoroutineScope.encode(source: ByteReadChannel): ByteReadChannel =
        source.deflated(gzip = true, coroutineContext = coroutineContext)

    override fun CoroutineScope.decode(source: ByteReadChannel): ByteReadChannel =
        inflate(source, gzip = false)

}

/**
 * Implementation of GZip [Encoder].
 */
val GZip: Encoder = object : Encoder {
    override fun CoroutineScope.encode(source: ByteReadChannel): ByteReadChannel =
        source.deflated(gzip = true, coroutineContext = coroutineContext)

    override fun CoroutineScope.decode(source: ByteReadChannel): ByteReadChannel = inflate(source)
}

private fun CoroutineScope.inflate(
    source: ByteReadChannel,
    gzip: Boolean = true
): ByteReadChannel = writer {
    val readBuffer = KtorDefaultPool.borrow()
    val writeBuffer = KtorDefaultPool.borrow()
    val inflater = Inflater(true)
    val checksum = CRC32()

    if (gzip) {
        val header = source.readPacket(GZIP_HEADER_SIZE)
        val magic = header.readShortLittleEndian()
        val format = header.readByte()
        val padding = header.readBytes()

        check(magic == GZIP_MAGIC) { "GZIP magic invalid: $magic" }
        check(format.toInt() == Deflater.DEFLATED) { "Deflater method unsupported: $format." }
        check(padding.contentEquals(GZIP_HEADER_PADDING)) { "Gzip padding invalid." }
    }

    try {
        var totalSize = 0
        while (!source.isClosedForRead) {
            if (source.readAvailable(readBuffer) <= 0) continue
            readBuffer.flip()

            inflater.setInput(readBuffer.array(), readBuffer.position(), readBuffer.remaining())

            while (!inflater.needsInput() && !inflater.finished()) {
                totalSize += inflater.inflateTo(channel, writeBuffer, checksum)
                readBuffer.position(readBuffer.limit() - inflater.remaining)
            }

            readBuffer.compact()
        }

        readBuffer.flip()

        while (!inflater.finished()) {
            totalSize += inflater.inflateTo(channel, writeBuffer, checksum)
            readBuffer.position(readBuffer.limit() - inflater.remaining)
        }

        if (gzip) {
            check(readBuffer.remaining() == 8) {
                "Expected 8 bytes in the trailer. Actual: ${readBuffer.remaining()} $"
            }

            readBuffer.order(java.nio.ByteOrder.LITTLE_ENDIAN)
            val expectedChecksum = readBuffer.getInt(readBuffer.position())
            val expectedSize = readBuffer.getInt(readBuffer.position() + 4)

            check(checksum.value.toInt() == expectedChecksum) { "Gzip checksum invalid." }
            check(totalSize == expectedSize) { "Gzip size invalid. Expected $expectedSize, actual $totalSize" }
        } else {
            check(!readBuffer.hasRemaining())
        }

    } catch (cause: Throwable) {
        throw cause
    } finally {
        inflater.end()
        KtorDefaultPool.recycle(readBuffer)
        KtorDefaultPool.recycle(writeBuffer)
    }
}.channel

private suspend fun Inflater.inflateTo(channel: ByteWriteChannel, buffer: ByteBuffer, checksum: Checksum): Int {
    buffer.clear()

    val inflated = inflate(buffer.array(), buffer.position(), buffer.remaining())
    buffer.position(buffer.position() + inflated)
    buffer.flip()

    checksum.updateKeepPosition(buffer)

    channel.writeFully(buffer)
    return inflated
}
