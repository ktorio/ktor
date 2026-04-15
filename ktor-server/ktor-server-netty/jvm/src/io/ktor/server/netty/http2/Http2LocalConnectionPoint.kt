/*
* Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.server.netty.http2

import io.ktor.http.*
import io.ktor.server.netty.http.*
import io.netty.handler.codec.http2.*
import java.net.*

internal class Http2LocalConnectionPoint(
    nettyHeaders: Http2Headers,
    localNetworkAddress: InetSocketAddress?,
    remoteNetworkAddress: InetSocketAddress?,
) : RequestConnectionPoint by HttpMultiplexedConnectionPoint(
    pseudoMethod = nettyHeaders.method(),
    pseudoScheme = nettyHeaders.scheme(),
    pseudoAuthority = nettyHeaders.authority(),
    pseudoPath = nettyHeaders.path(),
    localNetworkAddress = localNetworkAddress,
    remoteNetworkAddress = remoteNetworkAddress,
    httpVersion = "HTTP/2",
)
