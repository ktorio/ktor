package io.ktor.util

import io.ktor.util.cio.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.io.*
import kotlinx.io.core.ByteOrder
import kotlinx.io.pool.*
import java.nio.ByteBuffer
import java.util.zip.*
import kotlin.coroutines.*


private const val GZIP_MAGIC = 0x8b1f
private val headerPadding = ByteArray(7)

private fun Deflater.deflate(outBuffer: ByteBuffer) {
    if (outBuffer.hasRemaining()) {
        val written = deflate(outBuffer.array(), outBuffer.arrayOffset() + outBuffer.position(), outBuffer.remaining())
        outBuffer.position(outBuffer.position() + written)
    }
}

private fun Deflater.setInput(buffer: ByteBuffer) {
    require(buffer.hasArray()) { "buffer need to be array-backed" }
    setInput(buffer.array(), buffer.arrayOffset() + buffer.position(), buffer.remaining())
}

private fun Checksum.updateKeepPosition(buffer: ByteBuffer) {
    require(buffer.hasArray()) { "buffer need to be array-backed" }
    update(buffer.array(), buffer.arrayOffset() + buffer.position(), buffer.remaining())
}

private suspend fun ByteWriteChannel.putGzipHeader() {
    writeShort(GZIP_MAGIC.toShort())
    writeByte(Deflater.DEFLATED.toByte())
    writeFully(headerPadding)
}

private suspend fun ByteWriteChannel.putGzipTrailer(crc: Checksum, deflater: Deflater) {
    writeInt(crc.value.toInt())
    writeInt(deflater.totalIn)
}

private suspend fun ByteWriteChannel.deflateWhile(deflater: Deflater, buffer: ByteBuffer, predicate: () -> Boolean) {
    while (predicate()) {
        buffer.clear()
        deflater.deflate(buffer)
        buffer.flip()
        writeFully(buffer)
    }
}

/**
 * Launch a coroutine on [coroutineContext] that does deflate compression
 * optionally doing CRC and writing GZIP header and trailer if [gzip] = `true`
 */
@KtorExperimentalAPI
@UseExperimental(ExperimentalCoroutinesApi::class)
fun ByteReadChannel.deflated(
    gzip: Boolean = true,
    pool: ObjectPool<ByteBuffer> = KtorDefaultPool,
    coroutineContext: CoroutineContext = Dispatchers.Unconfined
): ByteReadChannel = GlobalScope.writer(coroutineContext, autoFlush = true) {
    channel.writeByteOrder = ByteOrder.LITTLE_ENDIAN
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
            deflater.setInput(input)
            channel.deflateWhile(deflater, compressed) { !deflater.needsInput() }
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
@KtorExperimentalAPI
fun ByteWriteChannel.deflated(
    gzip: Boolean = true,
    pool: ObjectPool<ByteBuffer> = KtorDefaultPool,
    coroutineContext: CoroutineContext = Dispatchers.Unconfined
): ByteWriteChannel = GlobalScope.reader(coroutineContext, autoFlush = true) {
    channel.deflated(gzip, pool, coroutineContext).joinTo(this@deflated, closeOnEnd = true)
}.channel
