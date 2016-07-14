package org.jetbrains.ktor.netty.http2

import io.netty.channel.*
import io.netty.handler.codec.http2.*
import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.host.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.netty.*
import org.jetbrains.ktor.nio.*

internal class NettyHttp2ApplicationResponse(val call: ApplicationCall, val context: ChannelHandlerContext) : BaseApplicationResponse(call) {

    private val responseHeaders = DefaultHttp2Headers().apply {
        status(HttpStatusCode.OK.value.toString())
    }

    override fun setStatus(statusCode: HttpStatusCode) {
        responseHeaders.status(statusCode.value.toString())
    }

    private val _channel: Lazy<WriteChannel> = lazy {
        context.executeInLoop {
            context.writeAndFlush(DefaultHttp2HeadersFrame(responseHeaders, false))
        }

        NettyHttp2WriteChannel(context)
    }

    fun ensureChannelClosed() {
        if (_channel.isInitialized()) {
            _channel.value.close()
        }
    }

    override fun channel() = _channel.value

    override val headers = object : ResponseHeaders() {
        override fun hostAppendHeader(name: String, value: String) {
            responseHeaders.add(name.toLowerCase(), value)
        }

        override fun getHostHeaderNames(): List<String> = responseHeaders.names().map { it.toString() }

        override fun getHostHeaderValues(name: String): List<String> = responseHeaders.getAll(name).map { it.toString() }
    }

}