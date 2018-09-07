package io.ktor.server.servlet

import io.ktor.http.*
import javax.servlet.http.*

internal class ServletConnectionPoint(val servletRequest: HttpServletRequest) : RequestConnectionPoint {
    override val uri = servletRequest.queryString.let { query ->
        if (query == null) {
            servletRequest.requestURI!!
        } else {
            "${servletRequest.requestURI}?$query"
        }
    }

    override val version: String
        get() = servletRequest.protocol

    override val method: HttpMethod
        get() = HttpMethod.parse(servletRequest.method)

    override val scheme: String
        get() = servletRequest.scheme ?: "http"

    override val port: Int
        get() = servletRequest.serverPort

    override val host: String
        get() = servletRequest.serverName ?: "localhost"

    override val remoteHost: String
        get() = servletRequest.remoteHost
}