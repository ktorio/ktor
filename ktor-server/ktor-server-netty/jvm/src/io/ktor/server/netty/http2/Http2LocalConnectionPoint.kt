/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.server.netty.http2

import io.ktor.http.*
import io.netty.handler.codec.http2.*
import java.net.*

internal class Http2LocalConnectionPoint(
    private val nettyHeaders: Http2Headers,
    private val localAddress: InetSocketAddress?,
    private val remoteAddress: InetSocketAddress?
) : RequestConnectionPoint {
    override val method: HttpMethod = nettyHeaders.method()?.let { HttpMethod.parse(it.toString()) } ?: HttpMethod.Get

    override val scheme: String
        get() = nettyHeaders.scheme()?.toString() ?: "http"

    override val version: String
        get() = "HTTP/2"

    override val uri: String
        get() = nettyHeaders.path()?.toString() ?: "/"

    override val host: String
        get() = nettyHeaders.authority()?.toString()?.substringBefore(":") ?: "localhost"

    override val port: Int
        get() = nettyHeaders.authority()?.toString()
            ?.substringAfter(":", "")?.takeIf { it.isNotEmpty() }?.toInt()
            ?: localAddress?.port
            ?: 80

    override val remoteHost: String
        get() = remoteAddress?.let {
            it.hostName ?: it.address.hostAddress
        } ?: "unknown"
}
