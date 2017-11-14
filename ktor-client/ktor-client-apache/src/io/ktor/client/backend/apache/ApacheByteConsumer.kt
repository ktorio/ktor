package io.ktor.client.backend.apache

import io.ktor.network.util.*
import io.ktor.util.*
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.channels.*
import kotlinx.coroutines.experimental.io.*
import org.apache.http.*
import org.apache.http.nio.*
import org.apache.http.nio.client.methods.*
import org.apache.http.protocol.*
import java.io.*
import java.nio.ByteBuffer
import java.util.concurrent.atomic.*

private val RESPONSE_CHANNEL_SIZE = 5

private data class ApacheResponseChunk(val buffer: ByteBuffer, val io: IOControl)

internal class ApacheByteConsumer(
        private val channel: ByteWriteChannel,
        private val block: (HttpResponse) -> Unit
) : AsyncByteConsumer<Unit>() {
    private val backendChannel = Channel<ApacheResponseChunk>(RESPONSE_CHANNEL_SIZE)
    private val channelSize = AtomicInteger(0)

    init {
        runResponseProcessing()
    }

    override fun buildResult(context: HttpContext) {
        backendChannel.close()
    }

    override fun onByteReceived(buffer: ByteBuffer, io: IOControl) {
        val data = buffer.copy()
        if (data.remaining() <= 0) return

        if (!backendChannel.offer(ApacheResponseChunk(data, io))) {
            throw IOException("backendChannel.offer() failed")
        }

        if (channelSize.incrementAndGet() >= RESPONSE_CHANNEL_SIZE) io.suspendInput()
    }

    override fun onResponseReceived(response: HttpResponse) = block(response)

    private fun runResponseProcessing() = launch(ioCoroutineDispatcher) {
        try {
            while (!backendChannel.isClosedForReceive) {
                val (buffer, io) = backendChannel.receiveOrNull() ?: break
                channel.writeFully(buffer)
                if (channelSize.decrementAndGet() < RESPONSE_CHANNEL_SIZE / 2) io.requestInput()
            }
        } catch (t: Throwable) {
            backendChannel.close(t)
            channel.close(t)
            return@launch
        }

        channel.close()
    }
}
