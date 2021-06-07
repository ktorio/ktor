package io.ktor.utils.io.jvm.javaio

import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import io.ktor.utils.io.pool.*
import kotlinx.coroutines.*
import java.io.*
import java.nio.*
import kotlin.coroutines.*

/**
 * Copies up to [limit] bytes from [this] input stream to CIO byte [channel] blocking on reading [this] stream
 * and suspending on [channel] if required
 *
 * @return number of bytes copied
 */
public suspend fun InputStream.copyTo(channel: ByteWriteChannel, limit: Long = Long.MAX_VALUE): Long {
    require(limit >= 0) { "Limit shouldn't be negative: $limit" }
    val buffer = ByteArrayPool.borrow()

    try {
        var copied = 0L
        val bufferSize = buffer.size.toLong()
        while (copied < limit) {
            val rc = read(buffer, 0, minOf(limit - copied, bufferSize).toInt())
            if (rc == -1) break
            else if (rc > 0) {
                channel.writeFully(buffer, 0, rc)
                copied += rc
            }
        }

        return copied
    } finally {
        ByteArrayPool.recycle(buffer)
    }
}

/**
 * Open a channel and launch a coroutine to copy bytes from the input stream to the channel.
 * Please note that it may block your async code when started on [Dispatchers.Unconfined]
 * since [InputStream] is blocking on it's nature
 */
@ExperimentalIoApi
@Suppress("BlockingMethodInNonBlockingContext")
public fun InputStream.toByteReadChannel(
    context: CoroutineContext = Dispatchers.IO,
    pool: ObjectPool<ByteBuffer>
): ByteReadChannel = GlobalScope.writer(context, autoFlush = true) {
    val buffer = pool.borrow()
    try {
        while (true) {
            buffer.clear()
            val readCount = read(buffer.array(), buffer.arrayOffset() + buffer.position(), buffer.remaining())
            if (readCount < 0) break
            if (readCount == 0) continue

            buffer.position(buffer.position() + readCount)
            buffer.flip()
            channel.writeFully(buffer)
        }
    } catch (cause: Throwable) {
        channel.close(cause)
    } finally {
        pool.recycle(buffer)
        close()
    }
}.channel

/**
 * Open a channel and launch a coroutine to copy bytes from the input stream to the channel.
 * Please note that it may block your async code when started on [Dispatchers.Unconfined]
 * since [InputStream] is blocking on it's nature
 */
@ExperimentalIoApi
@Suppress("BlockingMethodInNonBlockingContext")
@JvmName("toByteReadChannelWithArrayPool")
public fun InputStream.toByteReadChannel(
    context: CoroutineContext = Dispatchers.IO,
    pool: ObjectPool<ByteArray> = ByteArrayPool
): ByteReadChannel = GlobalScope.writer(context, autoFlush = true) {
    val buffer = pool.borrow()
    try {
        while (true) {
            val readCount = read(buffer, 0, buffer.size)
            if (readCount < 0) break
            if (readCount == 0) continue

            channel.writeFully(buffer, 0, readCount)
        }
    } catch (cause: Throwable) {
        channel.close(cause)
    } finally {
        pool.recycle(buffer)
        close()
    }
}.channel
