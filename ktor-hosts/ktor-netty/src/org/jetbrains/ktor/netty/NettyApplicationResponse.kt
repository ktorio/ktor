package org.jetbrains.ktor.netty

import io.netty.channel.*
import io.netty.handler.codec.http.*
import io.netty.handler.codec.http.HttpHeaders
import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.host.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.interception.*
import org.jetbrains.ktor.nio.*
import java.io.*
import java.util.concurrent.atomic.*

internal class NettyApplicationResponse(val request: HttpRequest, val response: HttpResponse, val context: ChannelHandlerContext) : BaseApplicationResponse() {
    @Volatile
    private var commited = false
    private val closed = AtomicBoolean(false)

    override val status = Interceptable1<HttpStatusCode, Unit> { status ->
        response.status = HttpResponseStatus(status.value, status.description)
    }

    override val channel = Interceptable0<AsyncWriteChannel> {
        setChunked()
        sendRequestMessage()

        NettyAsyncWriteChannel(request, this, context)
    }

    override val stream = Interceptable1<OutputStream.() -> Unit, Unit> { body ->
        setChunked()
        sendRequestMessage()

        NettyAsyncStream(request, this, context).use(body)
    }

    override val headers: ResponseHeaders = object : ResponseHeaders() {
        override fun hostAppendHeader(name: String, value: String) {
            response.headers().add(name, value)
        }
        override fun getHostHeaderNames(): List<String> = response.headers().map { it.key }
        override fun getHostHeaderValues(name: String): List<String> = response.headers().getAll(name) ?: emptyList()
    }

    override fun status(): HttpStatusCode? = response.status?.let { HttpStatusCode(it.code(), it.reasonPhrase()) }

    fun sendRequestMessage(): ChannelFuture? {
        if (!commited) {
            val f = context.writeAndFlush(response)
            commited = true
            return f
        }
        return null
    }

    fun finalize() {
        sendRequestMessage()
        context.flush()
        if (closed.compareAndSet(false, true)) {
            context.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT).scheduleClose()
        }
    }

    private fun ChannelFuture.scheduleClose() {
        if (!HttpHeaders.isKeepAlive(request)) {
            addListener(ChannelFutureListener.CLOSE)
        }
    }

    private fun setChunked() {
        if (commited) {
            if (!response.headers().contains(HttpHeaders.Names.TRANSFER_ENCODING, HttpHeaders.Values.CHUNKED, true)) {
                throw IllegalStateException("Already commited")
            }
        }
        HttpHeaders.setTransferEncodingChunked(request)
    }
}