/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.util

import io.ktor.util.cio.*
import io.ktor.utils.io.*
import io.ktor.utils.io.bits.*
import io.ktor.utils.io.pool.*
import kotlinx.coroutines.*
import java.nio.ByteBuffer
import java.util.zip.*
import kotlin.coroutines.*

internal const val GZIP_MAGIC: Short = 0x8b1f.toShort()
internal val GZIP_HEADER_PADDING: ByteArray = ByteArray(7)

private fun Deflater.deflateTo(outBuffer: ByteBuffer) {
    if (outBuffer.hasRemaining()) {
        val written = deflate(outBuffer.array(), outBuffer.arrayOffset() + outBuffer.position(), outBuffer.remaining())
        outBuffer.position(outBuffer.position() + written)
    }
}

private fun Deflater.setInputBuffer(buffer: ByteBuffer) {
    require(buffer.hasArray()) { "buffer need to be array-backed" }
    setInput(buffer.array(), buffer.arrayOffset() + buffer.position(), buffer.remaining())
}

internal fun Checksum.updateKeepPosition(buffer: ByteBuffer) {
    require(buffer.hasArray()) { "buffer need to be array-backed" }
    update(buffer.array(), buffer.arrayOffset() + buffer.position(), buffer.remaining())
}

private suspend fun ByteWriteChannel.putGzipHeader() {
    writeShort(GZIP_MAGIC.reverseByteOrder())
    writeByte(Deflater.DEFLATED.toByte())
    writeFully(GZIP_HEADER_PADDING)
}

private suspend fun ByteWriteChannel.putGzipTrailer(crc: Checksum, deflater: Deflater) {
    writeInt(crc.value.toInt().reverseByteOrder())
    writeInt(deflater.totalIn.reverseByteOrder())
}

private suspend fun ByteWriteChannel.deflateWhile(deflater: Deflater, buffer: ByteBuffer, predicate: () -> Boolean) {
    while (predicate()) {
        buffer.clear()
        deflater.deflateTo(buffer)
        buffer.flip()
        writeFully(buffer)
    }
}

/**
 * Launch a coroutine on [coroutineContext] that does deflate compression
 * optionally doing CRC and writing GZIP header and trailer if [gzip] = `true`
 */
public fun ByteReadChannel.deflated(
    gzip: Boolean = true,
    pool: ObjectPool<ByteBuffer> = KtorDefaultPool,
    coroutineContext: CoroutineContext = Dispatchers.Unconfined
): ByteReadChannel = GlobalScope.writer(coroutineContext, autoFlush = true) {
    val crc = CRC32()
    val deflater = Deflater(Deflater.BEST_COMPRESSION, true)
    val input = pool.borrow()
    val compressed = pool.borrow()

    try {
        if (gzip) {
            channel.putGzipHeader()
        }

        while (!isClosedForRead) {
            input.clear()
            if (readAvailable(input) <= 0) continue
            input.flip()

            crc.updateKeepPosition(input)
            deflater.setInputBuffer(input)
            channel.deflateWhile(deflater, compressed) { !deflater.needsInput() }
        }

        if (this is ByteChannel) {
            closedCause?.let { throw it }
        }

        deflater.finish()
        channel.deflateWhile(deflater, compressed) { !deflater.finished() }

        if (gzip) {
            channel.putGzipTrailer(crc, deflater)
        }
    } finally {
        deflater.end()
        pool.recycle(input)
        pool.recycle(compressed)
    }
}.channel

/**
 * Launch a coroutine on [coroutineContext] that does deflate compression
 * optionally doing CRC and writing GZIP header and trailer if [gzip] = `true`
 */
public fun ByteWriteChannel.deflated(
    gzip: Boolean = true,
    pool: ObjectPool<ByteBuffer> = KtorDefaultPool,
    coroutineContext: CoroutineContext = Dispatchers.Unconfined
): ByteWriteChannel = GlobalScope.reader(coroutineContext, autoFlush = true) {
    channel.deflated(gzip, pool, coroutineContext).copyTo(this@deflated)
}.channel
