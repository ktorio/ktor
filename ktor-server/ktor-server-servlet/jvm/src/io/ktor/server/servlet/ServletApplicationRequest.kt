/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.server.servlet

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.server.engine.*
import javax.servlet.http.*

@Suppress("KDocMissingDocumentation")
@EngineAPI
public abstract class ServletApplicationRequest(
    call: ApplicationCall,
    public val servletRequest: HttpServletRequest
) : BaseApplicationRequest(call) {
    override val local: RequestConnectionPoint = ServletConnectionPoint(servletRequest)

    override val queryParameters: Parameters by lazy {
        servletRequest.queryString?.let { parseQueryString(it) } ?: Parameters.Empty
    }

    override val headers: Headers = ServletApplicationRequestHeaders(servletRequest)

    @Suppress("LeakingThis") // this is safe because we don't access any content in the request
    override val cookies: RequestCookies = ServletApplicationRequestCookies(servletRequest, this)
}
