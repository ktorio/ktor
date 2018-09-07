package io.ktor.util.cio

import io.ktor.util.*
import kotlinx.coroutines.*
import kotlinx.coroutines.io.*
import kotlinx.io.pool.*
import java.io.*
import java.nio.*
import kotlin.coroutines.*

/**
 * Open a channel and launch a coroutine to copy bytes from the input stream to the channel.
 * Please note that it may block your async code when started on [Dispatchers.Unconfined]
 * since [InputStream] is blocking on it's nature
 */
@KtorExperimentalAPI
fun InputStream.toByteReadChannel(
    pool: ObjectPool<ByteBuffer> = KtorDefaultPool,
    context: CoroutineContext = Dispatchers.Unconfined,
    parent: Job = Job()
): ByteReadChannel = CoroutineScope(context).writer(parent, autoFlush = true) {
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
