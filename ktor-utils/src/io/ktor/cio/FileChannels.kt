package io.ktor.cio

import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.io.*
import kotlinx.io.pool.*
import java.io.*
import java.nio.ByteBuffer
import java.nio.file.*
import kotlin.math.*


fun File.readChannel(
        start: Long = 0,
        endInclusive: Long = -1,
        pool: ObjectPool<ByteBuffer> = EmptyByteBufferPool
): ByteReadChannel {
    val file = RandomAccessFile(this@readChannel, "r")
    return writer(Unconfined, autoFlush = true) {
        require(start >= 0L) { "start position shouldn't be negative but it is $start" }
        require(endInclusive <= file.length() - 1) { "endInclusive points to the position out of the file: file size = ${file.length()}, endInclusive = $endInclusive" }
        file.use {
            pool.use { buffer ->
                file.seek(start)
                val lastIndex = file.length() - 1
                val end = if (endInclusive >= 0) min(endInclusive, lastIndex) else lastIndex

                var position = start
                while (position <= end) {
                    buffer.clear()
                    val limit = min(buffer.remaining().toLong(), end - position + 1).toInt()
                    val count = file.read(buffer.array(), buffer.arrayOffset() + buffer.position(), limit)

                    if (count < 0) break
                    buffer.position(buffer.position() + count)
                    buffer.flip()

                    channel.writeFully(buffer)
                    position += limit
                }
            }
        }
    }.channel
}

fun File.writeChannel(pool: ObjectPool<ByteBuffer> = EmptyByteBufferPool): ByteWriteChannel = reader(Unconfined, autoFlush = true) {
    RandomAccessFile(this@writeChannel, "rw").use { file ->
        pool.use { buffer ->
            while (!channel.isClosedForRead) {
                buffer.clear()
                channel.readAvailable(buffer)
                buffer.flip()
                file.write(buffer.array(), buffer.arrayOffset() + buffer.position(), buffer.limit())
            }
        }
    }
}.channel

fun Path.readChannel(start: Long, endInclusive: Long): ByteReadChannel = toFile().readChannel(start, endInclusive)

fun Path.readChannel(): ByteReadChannel = toFile().readChannel()
