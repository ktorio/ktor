package org.jetbrains.ktor.netty

import io.netty.channel.*
import io.netty.handler.codec.http.*
import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.host.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.http.HttpHeaders
import org.jetbrains.ktor.response.*
import java.util.concurrent.atomic.*

internal class NettyApplicationResponse(call: ApplicationCall, responsePipeline: RespondPipeline, val request: HttpRequest, val response: HttpResponse, val context: ChannelHandlerContext) : BaseApplicationResponse(call, responsePipeline) {
    @Volatile
    private var committed = false
    private val closed = AtomicBoolean(false)

    override fun setStatus(statusCode: HttpStatusCode) {
        response.status = HttpResponseStatus(statusCode.value, statusCode.description)
    }

    internal val channelLazy = lazy {
        context.executeInLoop {
            setChunked()
            sendResponseMessage()
        }

        NettyWriteChannel(request, this, context)
    }

    override fun channel() = channelLazy.value

    override val headers: ResponseHeaders = object : ResponseHeaders() {
        override fun hostAppendHeader(name: String, value: String) {
            if (committed)
                throw UnsupportedOperationException("Headers can no longer be set because response was already completed")
            response.headers().add(name, value)
        }
        override fun getHostHeaderNames(): List<String> = response.headers().map { it.key }
        override fun getHostHeaderValues(name: String): List<String> = response.headers().getAll(name) ?: emptyList()
    }

    fun sendResponseMessage(): ChannelFuture? {
        if (!committed) {
            if (!HttpUtil.isTransferEncodingChunked(response)) {
                HttpUtil.setContentLength(response, 0L)
            }
            val f = context.writeAndFlush(response)
            committed = true
            return f
        }
        return null
    }

    fun finalize() {
        context.executeInLoop {
            sendResponseMessage()
            context.flush()
            if (closed.compareAndSet(false, true)) {
                context.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT).scheduleClose()
            }
            context.channel().config().isAutoRead = true
            context.read()
            if (channelLazy.isInitialized()) {
                channelLazy.value.close()
            }
        }
    }

    private fun ChannelFuture.scheduleClose() {
        if (!HttpUtil.isKeepAlive(request)) {
            addListener(ChannelFutureListener.CLOSE)
        }
    }

    private fun setChunked() {
        if (committed) {
            if (!response.headers().contains(HttpHeaders.TransferEncoding, HttpHeaderValues.CHUNKED, true)) {
                throw IllegalStateException("Already committed")
            }
        }
        if (response.status().code() != HttpStatusCode.SwitchingProtocols.value) {
            HttpUtil.setTransferEncodingChunked(response, true)
        }
    }
}