package io.ktor.server.servlet

import io.ktor.request.*
import io.ktor.server.engine.*
import javax.servlet.http.*

@Suppress("KDocMissingDocumentation")
@EngineAPI
class ServletApplicationRequestCookies(private val servletRequest: HttpServletRequest, request: ApplicationRequest) : RequestCookies(request) {
    override fun fetchCookies(): Map<String, String> {
        return servletRequest.cookies?.associateBy({ it.name }, { it.value }) ?: emptyMap()
    }
}
