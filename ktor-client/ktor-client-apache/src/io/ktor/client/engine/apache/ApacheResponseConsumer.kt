package io.ktor.client.engine.apache

import io.ktor.client.utils.*
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.channels.*
import kotlinx.coroutines.experimental.io.*
import org.apache.http.*
import org.apache.http.entity.*
import org.apache.http.nio.*
import org.apache.http.nio.protocol.*
import org.apache.http.protocol.*
import java.io.*
import java.nio.ByteBuffer
import java.util.concurrent.atomic.*
import java.util.concurrent.locks.*
import kotlin.concurrent.*
import kotlin.coroutines.experimental.*

private val MAX_QUEUE_LENGTH: Int = 65 * 1024 / DEFAULT_HTTP_BUFFER_SIZE

private data class ApacheResponseChunk(val buffer: ByteBuffer, val io: IOControl?)

internal class ApacheResponseConsumer(
        private val channel: ByteWriteChannel,
        private val dispatcher: CoroutineContext,
        private val block: (ApacheEngineResponse) -> Unit
) : AbstractAsyncResponseConsumer<Unit>() {
    private val backendChannel = Channel<ApacheResponseChunk>(Channel.UNLIMITED)
    private var current: ByteBuffer = HttpClientDefaultPool.borrow()
    private val released = AtomicBoolean(false)
    private val lock = ReentrantLock()
    private var channelSize: Int = 0

    init {
        runResponseProcessing()
    }

    override fun onResponseReceived(response: HttpResponse) = block(ApacheEngineResponse(response, Closeable { release() }))

    override fun releaseResources() = Unit

    fun release(throwable: Throwable? = null) {
        if (!released.compareAndSet(false, true)) return

        try {
            if (current.position() > 0) {
                current.flip()
                if (!backendChannel.offer(ApacheResponseChunk(current, null))) {
                    HttpClientDefaultPool.recycle(current)
                    throw IOException("backendChannel.offer() failed")
                }
            } else HttpClientDefaultPool.recycle(current)
        } finally {
            backendChannel.close(throwable)
        }
    }

    override fun buildResult(context: HttpContext) = Unit

    override fun onContentReceived(decoder: ContentDecoder, ioctrl: IOControl) {
        val read = decoder.read(current)
        if (read <= 0 || current.hasRemaining()) return

        current.flip()
        if (!backendChannel.offer(ApacheResponseChunk(current, ioctrl))) {
            throw IOException("backendChannel.offer() failed")
        }

        current = HttpClientDefaultPool.borrow()
        lock.withLock {
            ++channelSize
            if (channelSize == MAX_QUEUE_LENGTH) ioctrl.suspendInput()
        }
    }

    override fun onEntityEnclosed(entity: HttpEntity, contentType: ContentType) {}

    private fun runResponseProcessing() = launch(dispatcher) {
        try {
            while (!backendChannel.isClosedForReceive) {
                val (buffer, io) = backendChannel.receiveOrNull() ?: break
                lock.withLock {
                    --channelSize
                    io?.requestInput()
                }

                channel.writeFully(buffer)
                HttpClientDefaultPool.recycle(buffer)
            }
        } catch (throwable: Throwable) {
            channel.close(throwable)
        } finally {
            channel.close()
        }
    }
}
