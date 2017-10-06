package io.ktor.netty.http2

import io.netty.channel.*
import io.netty.handler.codec.http.*
import io.netty.handler.codec.http.multipart.*
import io.netty.handler.codec.http2.*
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.channels.*
import kotlinx.coroutines.experimental.io.*
import io.ktor.application.*
import io.ktor.cio.*
import io.ktor.content.*
import io.ktor.host.*
import io.ktor.http.*
import io.ktor.netty.*
import io.ktor.pipeline.*
import io.ktor.request.*
import io.ktor.util.*
import io.netty.handler.codec.http.HttpMethod
import java.net.*

internal class NettyHttp2ApplicationRequest(
        call: ApplicationCall,
        context: ChannelHandlerContext,
        val nettyHeaders: Http2Headers,
        val contentByteChannel: ByteChannel = ByteChannel())
    : NettyApplicationRequest(call, context, contentByteChannel, nettyHeaders[":path"]?.toString() ?: "/", keepAlive = true) {

    override val headers: ValuesMap by lazy { ValuesMap.build(caseInsensitiveKey = true) { nettyHeaders.forEach { append(it.key.toString(), it.value.toString()) } } }

    val contentActor = actor<Http2DataFrame>(Unconfined, kotlinx.coroutines.experimental.channels.Channel.UNLIMITED) {
        http2frameLoop(contentByteChannel)
    }

    override val local = Http2LocalConnectionPoint(nettyHeaders, context.channel().localAddress() as? InetSocketAddress)

    override val cookies: RequestCookies
        get() = throw UnsupportedOperationException()

    override fun receiveContent() = NettyHttpIncomingContent(this)

    override fun newDecoder(): HttpPostMultipartRequestDecoder {
        val hh = DefaultHttpHeaders(false)
        for ((name, value) in nettyHeaders) {
            hh.add(name, value)
        }

        val request = DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, uri, hh)
        return HttpPostMultipartRequestDecoder(request)
    }
}

