package io.ktor.server.netty.http2

import io.ktor.http.*
import io.netty.handler.codec.http2.*
import java.net.*

internal class Http2LocalConnectionPoint(private val nettyHeaders: Http2Headers, private val address: InetSocketAddress?) : RequestConnectionPoint {
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
                ?: address?.port
                ?: 80

    override val remoteHost: String
        get() = "unknown" // TODO
}