package org.jetbrains.ktor.servlet

import org.jetbrains.ktor.request.*
import javax.servlet.http.*

class ServletApplicationRequestCookies(val servletRequest: HttpServletRequest, request: ApplicationRequest) : RequestCookies(request) {
    override fun fetchCookies(): Map<String, String> {
        return servletRequest.cookies?.associateBy({ it.name }, { it.value }) ?: emptyMap()
    }
}