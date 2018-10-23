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
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.io.*
import java.net.*
import kotlin.coroutines.*

internal class NettyHttp2ApplicationRequest(
    call: ApplicationCall,
    coroutineContext: CoroutineContext,
    context: ChannelHandlerContext,
    val nettyHeaders: Http2Headers,
    val contentByteChannel: ByteChannel = ByteChannel()
) : NettyApplicationRequest(
    call,
    coroutineContext,
    context,
    contentByteChannel,
    nettyHeaders[":path"]?.toString() ?: "/",
    keepAlive = true
) {

    override val headers: Headers by lazy {
        Headers.build {
            nettyHeaders.forEach {
                append(
                    it.key.toString(),
                    it.value.toString()
                )
            }
        }
    }

    @UseExperimental(ObsoleteCoroutinesApi::class)
    val contentActor = actor<Http2DataFrame>(
        Dispatchers.Unconfined, kotlinx.coroutines.channels.Channel.UNLIMITED
    ) {
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

