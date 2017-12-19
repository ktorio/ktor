package io.ktor.cio

import kotlinx.coroutines.experimental.io.*
import java.io.*


open class ChannelIOException(message: String, exception: Exception) : IOException(message, exception)
class ChannelWriteException(message: String = "Cannot write to a channel", exception: Exception) : ChannelIOException(message, exception)
class ChannelReadException(message: String = "Cannot read from a channel", exception: Exception) : ChannelIOException(message, exception)

suspend fun ByteReadChannel.copyAndClose(dst: ByteWriteChannel, limit: Long = Long.MAX_VALUE): Long {
    val count = copyTo(dst, limit)
    dst.close()
    return count
}
