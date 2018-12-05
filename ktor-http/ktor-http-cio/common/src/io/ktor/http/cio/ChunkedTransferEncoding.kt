package io.ktor.http.cio

import io.ktor.http.cio.internals.*
import kotlinx.coroutines.*
import kotlinx.coroutines.io.*
import kotlinx.io.core.*
import kotlinx.io.core.ByteOrder
import kotlinx.io.pool.*
import kotlin.coroutines.*

private const val MAX_CHUNK_SIZE_LENGTH = 128
private const val CHUNK_BUFFER_POOL_SIZE = 2048

private val ChunkSizeBufferPool: ObjectPool<StringBuilder> =
    object : DefaultPool<StringBuilder>(CHUNK_BUFFER_POOL_SIZE) {
        override fun produceInstance(): StringBuilder = StringBuilder(MAX_CHUNK_SIZE_LENGTH)
        override fun clearInstance(instance: StringBuilder) = instance.apply { clear() }
    }

/**
 * Decoder job type
 */
typealias DecoderJob = WriterJob

/**
 * Start a chunked stream decoder coroutine
 */
fun CoroutineScope.decodeChunked(input: ByteReadChannel): DecoderJob = writer(coroutineContext) {
    decodeChunked(input, channel)
}

/**
 * Chunked stream decoding loop
 */
suspend fun decodeChunked(input: ByteReadChannel, out: ByteWriteChannel) {
    val chunkSizeBuffer = ChunkSizeBufferPool.borrow()

    try {
        while (true) {
            chunkSizeBuffer.clear()
            if (!input.readUTF8LineTo(chunkSizeBuffer, MAX_CHUNK_SIZE_LENGTH)) {
                throw EOFException("Chunked stream has ended unexpectedly: no chunk size")
            } else if (chunkSizeBuffer.isEmpty()) {
                throw EOFException("Invalid chunk size: empty")
            }

            val chunkSize =
                if (chunkSizeBuffer.length == 1 && chunkSizeBuffer[0] == '0') 0
                else chunkSizeBuffer.parseHexLong()

            if (chunkSize > 0) {
                input.copyTo(out, chunkSize)
                out.flush()
            }

            chunkSizeBuffer.clear()
            if (!input.readUTF8LineTo(chunkSizeBuffer, 2)) {
                throw EOFException("Invalid chunk: content block of size $chunkSize ended unexpectedly")
            }
            if (chunkSizeBuffer.isNotEmpty()) {
                throw EOFException("Invalid chunk: content block should end with CR+LF")
            }

            if (chunkSize == 0L) break
        }
    } catch (t: Throwable) {
        out.close(t)
        throw t
    } finally {
        ChunkSizeBufferPool.recycle(chunkSizeBuffer)
        out.close()
    }
}

/**
 * Encoder job type
 */
typealias EncoderJob = ReaderJob

/**
 * Start chunked stream encoding coroutine
 */
suspend fun encodeChunked(
    output: ByteWriteChannel,
    coroutineContext: CoroutineContext
): EncoderJob = GlobalScope.reader(coroutineContext, autoFlush = false) {
    encodeChunked(output, channel)
}

/**
 * Chunked stream encoding loop
 */
suspend fun encodeChunked(output: ByteWriteChannel, input: ByteReadChannel) {
    val view = IoBuffer.Pool.borrow()
    view.byteOrder = ByteOrder.BIG_ENDIAN

    try {
        input.readSuspendableSession {
            while (await()) {
                val content = request() ?: return@readSuspendableSession

                view.resetForWrite()
                view.writeIntHex(content.readRemaining)
                view.writeShort(CrLfShort)

                output.writeFully(view)
                output.writeFully(content)
                output.writeFully(CrLf)

                if (availableForRead == 0) output.flush()
            }
        }

        output.writeFully(LastChunkBytes)
    } catch (cause: Throwable) {
        output.close(cause)
    } finally {
        output.flush()
        view.release(IoBuffer.Pool)
    }
}

private const val CrLfShort: Short = 0x0d0a

private val CrLf = "\r\n".toByteArray()
private val LastChunkBytes = "0\r\n\r\n".toByteArray()
