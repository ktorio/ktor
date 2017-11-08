package io.ktor.client.backend.jetty

import io.ktor.client.utils.*
import io.ktor.http.*
import io.ktor.network.util.*
import io.ktor.util.*
import jdk.nashorn.internal.ir.annotations.*
import kotlinx.coroutines.experimental.channels.Channel
import kotlinx.coroutines.experimental.future.*
import kotlinx.coroutines.experimental.io.*
import org.eclipse.jetty.http.*
import org.eclipse.jetty.http2.*
import org.eclipse.jetty.http2.api.*
import org.eclipse.jetty.http2.frames.*
import org.eclipse.jetty.util.*
import java.io.*
import java.nio.ByteBuffer
import java.nio.channels.*
import java.util.concurrent.*


data class StatusWithHeaders(val statusCode: HttpStatusCode, val headers: Headers)

internal class HttpChannelListener : Stream.Listener {
    val channel: Channel<ByteBuffer> = Channel(Channel.UNLIMITED)
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
            if (frame.data.remaining() > 0 && !channel.offer(frame.data.copy())) {
                throw IllegalStateException("data.offer() failed")
            }

            if (frame.isEndStream) channel.close()
            callback.succeeded()
        } catch (t: Throwable) {
            callback.failed(t)
        }
    }

    override fun onHeaders(stream: Stream, frame: HeadersFrame) {
        frame.metaData.fields.forEach { field ->
            headersBuilder.append(field.name, field.value)
        }

        if (frame.isEndStream) channel.close()

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

internal class Http2Response {
    val listener: HttpChannelListener = HttpChannelListener()

    private val body: Channel<ByteBuffer> get() = listener.channel

    suspend fun awaitHeaders(): StatusWithHeaders = listener.awaitHeaders()

    fun channel(): ByteReadChannel {
        return writer(ioCoroutineDispatcher) {
            while (true) {
                val buffer = body.receiveOrNull() ?: break
                channel.writeFully(buffer)
            }

            body.close()
        }.channel
    }

    fun close() {
        body.close()
    }
}
