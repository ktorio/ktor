/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.jetty.jakarta

import io.ktor.server.request.*
import org.eclipse.jetty.server.*

public class JettyRequestCookies(
    request: JettyApplicationRequest,
    private val jettyRequest: Request
) : RequestCookies(request) {
    override fun fetchCookies(): Map<String, String> {
        return Request.getCookies(jettyRequest).associate { it.name to it.value }
    }
}
