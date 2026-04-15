/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.netty.http

import io.ktor.http.*
import java.net.*

/**
 * A shared [RequestConnectionPoint] implementation for multiplexed HTTP protocols (HTTP/2 and HTTP/3).
 *
 * Both HTTP/2 and HTTP/3 use pseudo-headers (`:method`, `:scheme`, `:authority`, `:path`) for request metadata.
 * This class abstracts over those pseudo-headers so the same connection point logic
 * can be reused regardless of the specific protocol version.
 */
internal class HttpMultiplexedConnectionPoint(
    private val pseudoMethod: CharSequence?,
    private val pseudoScheme: CharSequence?,
    private val pseudoAuthority: CharSequence?,
    private val pseudoPath: CharSequence?,
    private val localNetworkAddress: InetSocketAddress?,
    private val remoteNetworkAddress: InetSocketAddress?,
    private val httpVersion: String,
) : RequestConnectionPoint {
    override val method: HttpMethod = pseudoMethod?.let { HttpMethod.parse(it.toString()) } ?: HttpMethod.Get

    override val scheme: String
        get() = pseudoScheme?.toString() ?: "http"

    override val version: String
        get() = httpVersion

    override val uri: String
        get() = pseudoPath?.toString() ?: "/"

    @Deprecated(
        "Use localHost or serverHost instead",
        level = DeprecationLevel.ERROR
    )
    override val host: String
        get() = pseudoAuthority?.toString()?.substringBefore(":") ?: "localhost"

    @Deprecated(
        "Use localPort or serverPort instead",
        level = DeprecationLevel.ERROR
    )
    override val port: Int
        get() = pseudoAuthority?.toString()
            ?.substringAfter(":", "")?.takeIf { it.isNotEmpty() }?.toInt()
            ?: localNetworkAddress?.port
            ?: 80

    override val localHost: String
        get() = localNetworkAddress?.let { it.hostName ?: it.hostString } ?: "localhost"
    override val serverHost: String
        get() = pseudoAuthority
            ?.toString()
            ?.substringBefore(":")
            ?: localHost
    override val localAddress: String
        get() = localNetworkAddress?.hostString ?: "localhost"

    private val defaultPort
        get() = URLProtocol.createOrDefault(scheme).defaultPort
    override val localPort: Int
        get() = localNetworkAddress?.port ?: defaultPort
    override val serverPort: Int
        get() = pseudoAuthority
            ?.toString()
            ?.substringAfter(":", defaultPort.toString())?.toInt()
            ?: localPort

    override val remoteHost: String
        get() = remoteNetworkAddress
            ?.let { it.hostName ?: it.address.hostAddress }
            ?: "unknown"
    override val remotePort: Int
        get() = remoteNetworkAddress?.port ?: 0
    override val remoteAddress: String
        get() = remoteNetworkAddress?.hostString ?: "unknown"

    override fun toString(): String =
        "HttpMultiplexedConnectionPoint(uri=$uri, method=$method, version=$version, localAddress=$localAddress, " +
            "localPort=$localPort, remoteAddress=$remoteAddress, remotePort=$remotePort)"
}
