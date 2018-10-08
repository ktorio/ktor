package io.ktor.server.servlet

import io.ktor.request.*
import javax.servlet.http.*

class ServletApplicationRequestCookies(private val servletRequest: HttpServletRequest, request: ApplicationRequest) : RequestCookies(request) {
    override fun fetchCookies(): Map<String, String> {
        return servletRequest.cookies?.associateBy({ it.name }, { it.value }) ?: emptyMap()
    }
}