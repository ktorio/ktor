/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.jetty.jakarta

import io.ktor.http.*
import io.ktor.utils.io.*
import org.eclipse.jetty.server.Request

internal class JettyConnectionPoint(
    request: Request
) : RequestConnectionPoint {
    @Deprecated("Use localHost or serverHost instead")
    override val host: String = request.httpURI.host

    override val localAddress: String = Request.getLocalAddr(request)

    override val localHost: String = Request.getServerName(request)

    override val localPort: Int = Request.getLocalPort(request)

    override val method: HttpMethod = HttpMethod.parse(request.method)

    @Deprecated("Use localPort or serverPort instead")
    override val port: Int = request.httpURI.port

    override val remoteAddress: String = Request.getRemoteAddr(request)

    override val remoteHost: String = Request.getServerName(request)

    override val remotePort: Int = Request.getRemotePort(request)

    override val scheme: String = request.httpURI.scheme

    override val serverHost: String = Request.getServerName(request)

    override val serverPort: Int = Request.getServerPort(request)

    override val uri: String = request.httpURI.pathQuery

    override val version: String = request.connectionMetaData.httpVersion.asString()
}
