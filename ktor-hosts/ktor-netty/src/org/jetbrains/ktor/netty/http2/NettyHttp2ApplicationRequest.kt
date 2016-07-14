package org.jetbrains.ktor.netty.http2

import io.netty.channel.*
import io.netty.handler.codec.http2.*
import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.nio.*
import org.jetbrains.ktor.request.*
import org.jetbrains.ktor.util.*
import java.net.*

internal class NettyHttp2ApplicationRequest(override val call: ApplicationCall, val context: ChannelHandlerContext, val streamId: Int, val nettyHeaders: Http2Headers) : ApplicationRequest {
    override val headers: ValuesMap by lazy { ValuesMap.build(caseInsensitiveKey = true) { nettyHeaders.forEach { append(it.key.toString(), it.value.toString()) } } }
    override val parameters: ValuesMap
        get() = ValuesMap.Empty // TODO

    override val local = object : RequestConnectionPoint {
        override val method: HttpMethod = nettyHeaders.method()?.let { HttpMethod.parse(it.toString()) } ?: HttpMethod.Get

        override val scheme: String
            get() = nettyHeaders.scheme()?.toString() ?: "http"

        override val version: String
            get() = "HTTP/2"

        override val uri: String
            get() = nettyHeaders.path()?.toString() ?: "/"

        override val host: String
            get() = nettyHeaders.authority()?.toString() ?: "localhost"

        override val port: Int
            get() = nettyHeaders.authority()?.toString()?.substringAfter(":")?.toInt()
                    ?: (context.channel().localAddress() as? InetSocketAddress)?.port
                    ?: 80

        override val remoteHost: String
            get() = "unknown" // TODO
    }

    override val cookies: RequestCookies
        get() = throw UnsupportedOperationException()

    val handler: NettyHttp2ReadChannel = NettyHttp2ReadChannel(streamId, context)

    override val content = object : RequestContent(this) {
        override fun getInputStream() = getReadChannel().asInputStream()
        override fun getMultiPartData(): MultiPartData {
            throw UnsupportedOperationException("not implemented") //To change body of created functions use File | Settings | File Templates.
        }

        override fun getReadChannel(): ReadChannel {
            return handler
        }
    }
}