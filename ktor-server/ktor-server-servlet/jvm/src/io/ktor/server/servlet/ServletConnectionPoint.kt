/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.servlet

import io.ktor.http.*
import javax.servlet.http.*

internal class ServletConnectionPoint(servletRequest: HttpServletRequest) : RequestConnectionPoint {
    override val uri = servletRequest.queryString.let { query ->
        if (query == null) {
            servletRequest.requestURI!!
        } else {
            "${servletRequest.requestURI}?$query"
        }
    }

    override val version: String = servletRequest.protocol

    override val method: HttpMethod = HttpMethod.parse(servletRequest.method)

    override val scheme: String = servletRequest.scheme ?: "http"

    override val port: Int = servletRequest.serverPort

    override val host: String = servletRequest.serverName ?: "localhost"

    override val remoteHost: String = servletRequest.remoteHost
}
