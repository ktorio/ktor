/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.servlet

import io.ktor.server.request.*
import javax.servlet.http.*

@Deprecated(
    message = "Renamed to ServletServerRequestCookies",
    replaceWith = ReplaceWith("ServletServerRequestCookies")
)
public typealias ServletApplicationRequestCookies = ServletRequestCookies

public class ServletRequestCookies(
    private val servletRequest: HttpServletRequest,
    request: PipelineRequest
) : RequestCookies(request) {
    override fun fetchCookies(): Map<String, String> {
        return servletRequest.cookies?.associateBy({ it.name }, { it.value }) ?: emptyMap()
    }
}
