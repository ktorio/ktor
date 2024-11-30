/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.jetty.jakarta

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.request.*
import io.ktor.utils.io.*
import io.ktor.utils.io.jvm.javaio.*
import org.eclipse.jetty.io.*
import org.eclipse.jetty.server.*

public class JettyApplicationRequest(
    call: PipelineCall,
    request: Request
) : BaseApplicationRequest(call) {

    override val cookies: RequestCookies = JettyRequestCookies(this, request)

    override val engineHeaders: Headers = JettyHeaders(request)

    override val engineReceiveChannel: ByteReadChannel = Content.Source.asInputStream(request).toByteReadChannel()

    override val local: RequestConnectionPoint = JettyConnectionPoint(request)

    override val queryParameters: Parameters by lazy { encodeParameters(rawQueryParameters) }

    override val rawQueryParameters: Parameters by lazy(LazyThreadSafetyMode.NONE) {
        val uri = request.httpURI.query ?: return@lazy Parameters.Empty
        parseQueryString(uri, decode = false)
    }
}
