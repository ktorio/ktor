/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.server.servlet

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.request.*
import javax.servlet.http.*

public abstract class ServletApplicationRequest(
    call: PipelineCall,
    public val servletRequest: HttpServletRequest
) : BaseApplicationRequest(call) {

    override val local: RequestConnectionPoint = ServletConnectionPoint(servletRequest)

    override val queryParameters: Parameters by lazy { encodeParameters(rawQueryParameters).toQueryParameters() }

    override val rawQueryParameters: Parameters by lazy(LazyThreadSafetyMode.NONE) {
        val uri = servletRequest.queryString ?: return@lazy Parameters.Empty
        parseQueryString(uri, decode = false)
    }

    override val engineHeaders: Headers = ServletApplicationRequestHeaders(servletRequest)

    @Suppress("LeakingThis") // this is safe because we don't access any content in the request
    override val cookies: RequestCookies = ServletApplicationRequestCookies(servletRequest, this)
}
