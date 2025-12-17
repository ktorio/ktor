/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.jetty.jakarta

import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import org.eclipse.jetty.http.MetaData
import org.eclipse.jetty.http2.ErrorCode
import org.eclipse.jetty.http2.HTTP2Session
import org.eclipse.jetty.http2.api.Stream
import org.eclipse.jetty.http2.frames.HeadersFrame
import org.eclipse.jetty.http2.frames.PushPromiseFrame
import org.eclipse.jetty.http2.frames.ResetFrame
import org.eclipse.jetty.util.Callback
import org.eclipse.jetty.util.Promise
import java.io.IOException
import java.nio.channels.ClosedChannelException
import java.util.concurrent.TimeoutException
import kotlin.coroutines.CoroutineContext

internal data class StatusWithHeaders(val statusCode: HttpStatusCode, val headers: Headers)

private data class JettyResponseChunk(val data: Stream.Data, val stream: Stream)

internal class JettyResponseListener(
    private val request: HttpRequestData,
    private val session: HTTP2Session,
    private val channel: ByteWriteChannel,
    private val callContext: CoroutineContext
) : Stream.Listener {
    private val headersBuilder: HeadersBuilder = HeadersBuilder()
    private val onHeadersReceived = CompletableDeferred<HttpStatusCode?>()
    private val backendChannel = Channel<JettyResponseChunk>(Channel.UNLIMITED)

    init {
        runResponseProcessing()
    }

    override fun onPush(stream: Stream, frame: PushPromiseFrame): Stream.Listener {
        stream.reset(ResetFrame(frame.promisedStreamId, ErrorCode.CANCEL_STREAM_ERROR.code), Callback.NOOP)
        return Stream.Listener.AUTO_DISCARD
    }

    override fun onIdleTimeout(
        stream: Stream?,
        x: TimeoutException?,
        promise: Promise<Boolean?>?
    ) {
        channel.close(x)
        backendChannel.close()
        promise?.succeeded(true)
    }

    override fun onReset(
        stream: Stream?,
        frame: ResetFrame?,
        callback: Callback?
    ) {
        val cause = when (val code = frame?.error ?: 0) {
            0 -> null
            ErrorCode.CANCEL_STREAM_ERROR.code -> ClosedChannelException()
            else -> {
                val ec = ErrorCode.from(code)
                IOException("Connection reset ${ec?.name ?: "with unknown error code $code"}")
            }
        }
        backendChannel.close(cause)
        onHeadersReceived.complete(null)
        callback?.succeeded()
    }

    override fun onDataAvailable(stream: Stream) {
        val streamData = stream.readData()
        if (streamData == null) {
            // No data available now, demand to be called back
            stream.demand()
            return
        }

        try {
            if (!backendChannel.trySend(JettyResponseChunk(streamData, stream)).isSuccess) {
                throw IOException("Failed to send response data to processing channel - channel may be full or closed")
            }
        } catch (cause: Throwable) {
            backendChannel.close(cause)
        }
    }

    override fun onFailure(stream: Stream?, error: Int, reason: String?, failure: Throwable?, callback: Callback?) {
        callback?.succeeded()

        val messagePrefix = reason ?: "HTTP/2 failure"
        val message = when (error) {
            0 -> messagePrefix
            else -> "$messagePrefix, code $error"
        }

        val cause = IOException(message, failure)
        backendChannel.close(cause)
        onHeadersReceived.completeExceptionally(cause)
    }

    override fun onHeaders(stream: Stream, frame: HeadersFrame) {
        frame.metaData.httpFields.forEach { field ->
            headersBuilder.append(field.name, field.value)
        }

        if (frame.isEndStream || request.method == HttpMethod.Head) {
            backendChannel.close()
        } else {
            // Signal that we want to receive DATA frames
            stream.demand()
        }

        onHeadersReceived.complete(
            (frame.metaData as? MetaData.Response)?.let {
                val (status, reason) = it.status to it.reason
                reason?.let { text -> HttpStatusCode(status, text) } ?: HttpStatusCode.fromValue(status)
            }
        )
    }

    suspend fun awaitHeaders(): StatusWithHeaders {
        val statusCode = onHeadersReceived.await() ?: throw IOException("Connection reset")
        return StatusWithHeaders(statusCode, headersBuilder.build())
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun runResponseProcessing() = CoroutineScope(callContext).launch {
        while (true) {
            val (data, stream) = backendChannel.receiveCatching().getOrNull() ?: break
            val frame = data.frame()
            val buffer = frame.byteBuffer
            try {
                if (buffer.remaining() > 0) {
                    channel.writeFully(buffer)
                }
                data.release()

                if (frame.isEndStream) {
                    backendChannel.close()
                } else {
                    // Continue demanding more DATA frames
                    stream.demand()
                }
            } catch (_: ClosedWriteChannelException) {
                session.endPoint.close()
                break
            } catch (cause: Throwable) {
                session.endPoint.close()
                throw cause
            }
        }
    }.invokeOnCompletion { cause ->
        channel.close(cause)
        backendChannel.close()
    }
}
