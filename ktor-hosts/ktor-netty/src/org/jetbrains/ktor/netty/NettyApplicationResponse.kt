package org.jetbrains.ktor.netty

import io.netty.channel.*
import io.netty.handler.codec.http.*
import io.netty.handler.codec.http.HttpHeaders
import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.content.*
import org.jetbrains.ktor.host.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.interception.*
import java.io.*

internal class NettyApplicationResponse(val request: HttpRequest, val response: HttpResponse, val context: ChannelHandlerContext) : BaseApplicationResponse() {
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

    init {
        send.intercept { obj, next ->
            // TODO pass through interceptors chain instead of direct context usage
            if (obj is LocalFileContent) {
                sendHeaders(obj)
                stream {
                    if (this is NettyAsyncStream) {
                        writeFile(obj.file, 0L, obj.file.length())
                    } else {
                        obj.file.inputStream().use { it.copyTo(this) }
                    }
                }

                ApplicationCallResult.Asynchronous
            } else if (obj is StreamContentProvider) {
                sendHeaders(obj)
                setChunked()

                stream {
                    if (this is NettyAsyncStream) {
                        writeStream(obj.stream())
                    } else {
                        obj.stream().use { it.copyTo(this) }
                    }
                }

                ApplicationCallResult.Asynchronous
            } else {
                next(obj)
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