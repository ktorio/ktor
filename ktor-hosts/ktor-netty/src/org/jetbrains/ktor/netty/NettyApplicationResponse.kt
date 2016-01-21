package org.jetbrains.ktor.netty

import io.netty.channel.*
import io.netty.handler.codec.http.*
import io.netty.handler.codec.http.HttpHeaders
import io.netty.handler.stream.*
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

        val stream = (object : OutputStream() {
            override fun write(b: Int) {
                context.write(DefaultHttpContent(context.alloc().buffer(1, 1).setByte(0, b).writerIndex(1)))
            }

            override fun write(b: ByteArray, off: Int, len: Int) {
                context.write(DefaultHttpContent(context.alloc().buffer(len, len).setBytes(0, b, off, len).writerIndex(len)))
            }

            override fun flush() {
                context.flush()
            }

            override fun close() {
                context.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT).scheduleClose()
            }
        }).buffered()
        stream.body()
        stream.close()
        ApplicationCallResult.Handled
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
                setChunked()
                sendRequestMessage()

                context.write(DefaultFileRegion(obj.file, 0L, obj.file.length()))
                context.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT).scheduleClose()
                ApplicationCallResult.Asynchronous
            } else if (obj is StreamContentProvider) {
                sendHeaders(obj)
                setChunked()
                sendRequestMessage()

                context.writeAndFlush(HttpChunkedInput(ChunkedStream(obj.stream().buffered()))).scheduleClose()
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