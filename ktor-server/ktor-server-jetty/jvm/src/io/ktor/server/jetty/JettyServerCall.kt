/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.jetty

import io.ktor.server.application.Server
import io.ktor.server.jetty.internal.*
import io.ktor.server.servlet.*
import io.ktor.utils.io.*
import org.eclipse.jetty.server.*
import javax.servlet.http.*
import kotlin.coroutines.*

@InternalAPI
@Deprecated(message = "Renamed to JettyServerCall", replaceWith = ReplaceWith("JettyServerCall"))
public typealias JettyApplicationCall = JettyServerCall

public class JettyServerCall(
    server: Server,
    request: Request,
    servletRequest: HttpServletRequest,
    servletResponse: HttpServletResponse,
    engineContext: CoroutineContext,
    userContext: CoroutineContext,
    coroutineContext: CoroutineContext
) : AsyncServletServerCall(
    server,
    servletRequest,
    servletResponse,
    engineContext,
    userContext,
    JettyUpgradeImpl,
    coroutineContext
) {

    override val response: JettyServerResponse = JettyServerResponse(
        this,
        servletRequest,
        servletResponse,
        engineContext,
        userContext,
        request,
        coroutineContext
    )

    init {
        putResponseAttribute()
    }
}
