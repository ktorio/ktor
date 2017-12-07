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
import java.util.concurrent.locks.*
import kotlin.concurrent.*
import kotlin.coroutines.experimental.*

private val MAX_QUEUE_LENGTH: Int = 65 * 1024 / DEFAULT_HTTP_BUFFER_SIZE

private data class ApacheResponseChunk(val buffer: ByteBuffer, val io: IOControl?)

internal class ApacheResponseConsumer(
        private val channel: ByteWriteChannel,
        private val dispatcher: CoroutineContext,
        private val parent: CompletableDeferred<Unit>,
        private val block: (HttpResponse) -> Unit
) : AbstractAsyncResponseConsumer<Unit>() {
    private val backendChannel = Channel<ApacheResponseChunk>(Channel.UNLIMITED)
    private var current: ByteBuffer = HttpClientDefaultPool.borrow()
    private val lock = ReentrantLock()
    private var channelSize: Int = 0

    init {
        runResponseProcessing()
    }

    override fun onResponseReceived(response: HttpResponse) = block(response)

    override fun releaseResources() {
        backendChannel.close()
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

    private fun runResponseProcessing() = launch(dispatcher, parent = parent) {
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

            channel.writeRemaining()
        } catch (throwable: Throwable) {
            channel.close(throwable)
        } finally {
            channel.close()
            HttpClientDefaultPool.recycle(current)
        }
    }.invokeOnCompletion {
        parent.complete(Unit)
    }

    private suspend fun ByteWriteChannel.writeRemaining() {
        if (current.remaining() == 0) return
        current.flip()
        writeFully(current)
    }
}
