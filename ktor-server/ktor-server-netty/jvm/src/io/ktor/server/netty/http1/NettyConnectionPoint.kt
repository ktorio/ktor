/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.netty.http1

import io.ktor.http.*
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.netty.channel.*
import io.netty.handler.codec.http.*
import java.net.*

internal class NettyConnectionPoint(val request: HttpRequest, val context: ChannelHandlerContext) : RequestConnectionPoint {
    override val version: String
        get() = request.protocolVersion().text()

    override val uri: String
        get() = request.uri()

    override val method: HttpMethod
        get() = HttpMethod.parse(request.method().name())

    override val scheme by lazy { if (context.pipeline().context("ssl") == null) "http" else "https" }

    override val host: String
        get() = request.headers().get(HttpHeaders.Host)?.substringBefore(":")
                ?: (context.channel().localAddress() as? InetSocketAddress)?.let {
                    it.hostName ?: it.address.hostAddress
                }
                ?: "localhost"

    override val port: Int
        get() = (context.channel().localAddress() as? InetSocketAddress)?.port ?: 80

    override val remoteHost: String
        get() = (context.channel().remoteAddress() as? InetSocketAddress)?.let {
            it.hostName ?: it.address.hostAddress
        } ?: "unknown"
}
