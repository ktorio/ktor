package io.ktor.cio

import java.io.*
import java.nio.*
import java.nio.charset.*

interface Channel : Closeable

interface ReadChannel : Channel {
    suspend fun read(dst: ByteBuffer): Int
}

interface WriteChannel : Channel {
    suspend fun write(src: ByteBuffer)
    suspend fun flush()
}

interface RandomAccessReadChannel : ReadChannel {
    val position: Long
    val size: Long

    suspend fun seek(position: Long)
}

suspend fun ReadChannel.copyTo(out: WriteChannel, bufferPool: ByteBufferPool = NoPool, bufferSize: Int = 8192): Int {
    val bufferTicket = bufferPool.allocate(bufferSize)
    val buffer = bufferTicket.buffer
    var bytes = 0
    try {
        while (read(buffer).also { bytes += it } != -1) {
            buffer.flip()
            out.write(buffer)
            buffer.clear()
        }
    } finally {
        bufferPool.release(bufferTicket)
    }
    return bytes + 1 // compensate for -1 as EOF
}

open class ChannelIOException(message: String, exception: Exception) : IOException(message, exception)
class ChannelWriteException(message: String = "Cannot write to a channel", exception: Exception) : ChannelIOException(message, exception)
class ChannelReadException(message: String = "Cannot read from a channel", exception: Exception) : ChannelIOException(message, exception)

suspend fun ReadChannel.readText(charset: Charset = Charsets.UTF_8): String {
    val buffer = ByteBufferWriteChannel()
    copyTo(buffer)
    return buffer.toByteArray().toString(charset)
}
