package io.ktor.server.netty.http2

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.server.netty.*
import io.netty.channel.*
import io.netty.handler.codec.http.*
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.multipart.*
import io.netty.handler.codec.http2.*
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.channels.*
import kotlinx.coroutines.experimental.io.*
import java.net.*

internal class NettyHttp2ApplicationRequest(
        call: ApplicationCall,
        context: ChannelHandlerContext,
        val nettyHeaders: Http2Headers,
        val contentByteChannel: ByteChannel = ByteChannel())
    : NettyApplicationRequest(call, context, contentByteChannel, nettyHeaders[":path"]?.toString() ?: "/", keepAlive = true) {

    override val headers: Headers by lazy { Headers.build { nettyHeaders.forEach { append(it.key.toString(), it.value.toString()) } } }

    val contentActor = actor<Http2DataFrame>(Unconfined, kotlinx.coroutines.experimental.channels.Channel.UNLIMITED) {
        http2frameLoop(contentByteChannel)
    }

    override val local = Http2LocalConnectionPoint(nettyHeaders, context.channel().localAddress() as? InetSocketAddress)

    override val cookies: RequestCookies
        get() = throw UnsupportedOperationException()

    override fun newDecoder(): HttpPostMultipartRequestDecoder {
        val hh = DefaultHttpHeaders(false)
        for ((name, value) in nettyHeaders) {
            hh.add(name, value)
        }

        val request = DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, uri, hh)
        return HttpPostMultipartRequestDecoder(request)
    }
}

