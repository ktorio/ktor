package io.ktor.http.cio

import io.ktor.http.cio.internals.*
import kotlinx.coroutines.experimental.io.*
import kotlinx.io.pool.*
import java.io.*
import kotlin.coroutines.experimental.*

private const val MAX_CHUNK_SIZE_LENGTH = 128
private const val CHUNK_BUFFER_POOL_SIZE = 2048

private val ChunkSizeBufferPool: ObjectPool<StringBuilder> = object : DefaultPool<StringBuilder>(CHUNK_BUFFER_POOL_SIZE) {
    override fun produceInstance(): StringBuilder = StringBuilder(MAX_CHUNK_SIZE_LENGTH)
    override fun clearInstance(instance: StringBuilder) = instance.apply { clear() }
}

typealias DecoderJob = WriterJob
suspend fun decodeChunked(input: ByteReadChannel, coroutineContext: CoroutineContext): DecoderJob {
    return writer(coroutineContext) {
        decodeChunked(input, channel)
    }
}

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

typealias EncoderJob = ReaderJob
suspend fun encodeChunked(output: ByteWriteChannel, coroutineContext: CoroutineContext): EncoderJob {
    return reader(coroutineContext) {
        encodeChunked(output, channel)
    }
}

suspend fun encodeChunked(output: ByteWriteChannel, input: ByteReadChannel) {
    val chunkSizeBuffer = ChunkSizeBufferPool.borrow()
    val buffer = DefaultByteBufferPool.borrow()

    try {
        while (true) {
            val size = input.readAvailable(buffer)
            if (size == -1) {
                break
            }

            buffer.flip()
            chunkSizeBuffer.append(size.toString(16))
            chunkSizeBuffer.append("\r\n")
            output.writeStringUtf8(chunkSizeBuffer)
            output.writeFully(buffer)
            output.writeFully(CrLf)
            output.flush()

            chunkSizeBuffer.clear()
            buffer.clear()
        }

        output.writeFully(LastChunkBytes)
    } catch (cause: Throwable) {
        output.close(cause)
    } finally {
        output.flush()
        DefaultByteBufferPool.recycle(buffer)
        ChunkSizeBufferPool.recycle(chunkSizeBuffer)
    }
}

private val CrLf = "\r\n".toByteArray()
private val LastChunkBytes = "0\r\n\r\n".toByteArray()

private fun StringBuilder.clear() {
    delete(0, length)
}