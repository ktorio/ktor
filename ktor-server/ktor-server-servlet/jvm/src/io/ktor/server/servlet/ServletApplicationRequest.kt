package io.ktor.server.servlet

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.server.engine.*
import javax.servlet.http.*

@Suppress("KDocMissingDocumentation")
@EngineAPI
abstract class ServletApplicationRequest(
    call: ApplicationCall,
    val servletRequest: HttpServletRequest
) : BaseApplicationRequest(call) {

    override val local: RequestConnectionPoint = ServletConnectionPoint(servletRequest)

    override val queryParameters by lazy {
        servletRequest.queryString?.let { parseQueryString(it) } ?: Parameters.Empty
    }

    override val headers: Headers = ServletApplicationRequestHeaders(servletRequest)
    override val cookies: RequestCookies = ServletApplicationRequestCookies(servletRequest, this)
}

