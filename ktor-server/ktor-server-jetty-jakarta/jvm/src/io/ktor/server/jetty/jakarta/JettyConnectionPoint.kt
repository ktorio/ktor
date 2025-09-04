/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.jetty.jakarta

import io.ktor.http.*
import io.ktor.utils.io.*
import org.eclipse.jetty.server.Request

internal class JettyConnectionPoint(
    private val request: Request
) : RequestConnectionPoint {
    @Deprecated("Use localHost or serverHost instead")
    override val host: String get() = request.httpURI.host

    override val localAddress: String get() = Request.getLocalAddr(request)

    override val localHost: String get() = Request.getServerName(request)

    override val localPort: Int get() = Request.getLocalPort(request)

    override val method: HttpMethod get() = HttpMethod.parse(request.method)

    @Deprecated("Use localPort or serverPort instead")
    override val port: Int get() = request.httpURI.port

    override val remoteAddress: String get() = Request.getRemoteAddr(request)

    override val remoteHost: String get() = Request.getServerName(request)

    override val remotePort: Int get() = Request.getRemotePort(request)

    override val scheme: String get() = request.httpURI.scheme

    override val serverHost: String get() = Request.getServerName(request)

    override val serverPort: Int get() = Request.getServerPort(request)

    override val uri: String get() = request.httpURI.pathQuery

    override val version: String get() = request.connectionMetaData.httpVersion.asString()
}
