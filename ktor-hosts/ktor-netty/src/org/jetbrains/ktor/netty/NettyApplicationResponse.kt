package org.jetbrains.ktor.netty

import io.netty.channel.*
import io.netty.handler.codec.http.*
import io.netty.handler.codec.http.HttpHeaders
import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.host.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.interception.*
import java.io.*
import java.nio.channels.*

internal class NettyApplicationResponse(call: ApplicationCall, val request: HttpRequest, val response: HttpResponse, val context: ChannelHandlerContext) : BaseApplicationResponse(call) {
    private var commited = false

    override val status = Interceptable1<HttpStatusCode, Unit> { status ->
        response.status = HttpResponseStatus(status.value, status.description)
    }

    override val stream = Interceptable1<OutputStream.() -> Unit, Unit> { body ->
        setChunked()
        sendRequestMessage()

        NettyAsyncStream(request, context).use(body)

        ApplicationCallResult.Asynchronous
    }

    override val headers: ResponseHeaders = object : ResponseHeaders() {
        override fun hostAppendHeader(name: String, value: String) {
            response.headers().add(name, value)
        }
        override fun getHostHeaderNames(): List<String> = response.headers().map { it.key }
        override fun getHostHeaderValues(name: String): List<String> = response.headers().getAll(name) ?: emptyList()
    }

    override fun status(): HttpStatusCode? = response.status?.let { HttpStatusCode(it.code(), it.reasonPhrase()) }

    override fun sendFile(file: File, position: Long, length: Long) {
        stream {
            if (this is NettyAsyncStream) {
                writeFile(file, position, length)
            } else {
                file.inputStream().use { it.copyTo(this) }
            }
        }
    }

    override fun sendAsyncChannel(channel: AsynchronousByteChannel) {
        stream {
            if (this is NettyAsyncStream) {
                writeAsyncChannel(channel)
            } else {
                Channels.newInputStream(channel).use { it.copyTo(this) }
            }
        }
    }

    override fun sendStream(stream: InputStream) {
        stream {
            if (this is NettyAsyncStream) {
                writeStream(stream)
            } else {
                stream.use { it.copyTo(this) }
            }
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

    fun sendRequestMessage(): ChannelFuture? {
        if (!commited) {
            val f = context.writeAndFlush(response)
            commited = true
            return f
        }
        return null
    }

    fun finalize() {
        val f = sendRequestMessage()
        context.flush()
        if (f != null) {
            context.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT).scheduleClose()
        }
    }
}