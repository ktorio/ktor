package io.ktor.client.backend.jetty

import io.ktor.cio.*
import io.ktor.client.utils.*
import io.ktor.http.*
import io.ktor.util.*
import kotlinx.coroutines.experimental.channels.Channel
import kotlinx.coroutines.experimental.future.*
import org.eclipse.jetty.http.*
import org.eclipse.jetty.http2.*
import org.eclipse.jetty.http2.api.*
import org.eclipse.jetty.http2.frames.*
import org.eclipse.jetty.util.*
import java.io.*
import java.nio.*
import java.nio.channels.*
import java.util.concurrent.*


data class ReadState(val buffer: ByteBuffer, val callback: Callback)
data class StatusWithHeaders(val statusCode: HttpStatusCode, val headers: Headers)

class HttpChannelListener : Stream.Listener {
    val channel: Channel<ReadState> = Channel(Channel.UNLIMITED)
    private val headersBuilder: HeadersBuilder = HeadersBuilder(caseInsensitiveKey = true)
    private val onHeadersReceived: CompletableFuture<HttpStatusCode?> = CompletableFuture()

    override fun onPush(stream: Stream, frame: PushPromiseFrame): Stream.Listener {
        stream.reset(ResetFrame(frame.promisedStreamId, ErrorCode.CANCEL_STREAM_ERROR.code), Callback.NOOP)
        return Ignore
    }

    override fun onReset(stream: Stream, frame: ResetFrame) {
        when (frame.error) {
            0 -> channel.close()
            ErrorCode.CANCEL_STREAM_ERROR.code -> channel.close(ClosedChannelException())
            else -> {
                val code = ErrorCode.from(frame.error)
                channel.close(IOException("Connection reset ${code?.name ?: "with unknown error code ${frame.error}"}"))
            }
        }

        onHeadersReceived.complete(null)
    }

    override fun onData(stream: Stream, frame: DataFrame, callback: Callback) {
        try {
            if (frame.data.remaining() > 0 && !channel.offer(ReadState(frame.data.copy(), callback))) {
                throw IllegalStateException("data.offer() failed")
            }

            if (frame.isEndStream) {
                channel.close()
            }
        } catch (t: Throwable) {
            callback.failed(t)
        }
    }

    override fun onHeaders(stream: Stream, frame: HeadersFrame) {
        frame.metaData.fields.forEach { field ->
            headersBuilder.append(field.name, field.value)
        }

        if (frame.isEndStream) {
            channel.close()
        }

        onHeadersReceived.complete((frame.metaData as? MetaData.Response)?.let {
            HttpStatusCode(it.status, it.reason ?: "")
        })
    }

    suspend fun awaitHeaders(): StatusWithHeaders {
        onHeadersReceived.await()
        val statusCode = onHeadersReceived.get() ?: throw IOException("Connection reset")
        return StatusWithHeaders(statusCode, headersBuilder.build())
    }

    companion object {
        private val Ignore = Stream.Listener.Adapter()
    }
}


class Http2ResponseChannel : ReadChannel {
    val listener: HttpChannelListener = HttpChannelListener()

    private val channel: Channel<ReadState> get() = listener.channel
    private var current: ReadState? = null

    suspend fun awaitHeaders(): StatusWithHeaders = listener.awaitHeaders()

    suspend override fun read(dst: ByteBuffer): Int {
        val frame = current ?: channel.poll()
        return when {
            frame != null -> readImpl(frame, dst)
            channel.isClosedForReceive -> -1
            else -> readSuspend(dst)
        }
    }

    private suspend fun readSuspend(dst: ByteBuffer): Int {
        val framePair = channel.receiveOrNull() ?: return -1
        return readImpl(framePair, dst)
    }

    private fun readImpl(state: ReadState, destination: ByteBuffer): Int {
        val buffer = state.buffer
        val readCount = buffer.moveTo(destination)

        if (buffer.hasRemaining()) {
            current = state
        } else {
            current = null
            state.callback.succeeded()
        }

        return readCount
    }

    override fun close() {
        channel.close()
    }

}
