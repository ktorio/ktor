/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:Suppress("DEPRECATION_ERROR")

package io.ktor.routing

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*

@Deprecated(
    message = "Moved to io.ktor.server.routing",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("RoutingApplicationCall", "io.ktor.server.routing.*")
)
public class RoutingApplicationCall(
    private val call: ApplicationCall,
    public val route: Route,
    receivePipeline: ApplicationReceivePipeline,
    responsePipeline: ApplicationSendPipeline,
    parameters: Parameters
)

@kotlin.Deprecated(
    message = "Moved to io.ktor.server.routing",
    level = kotlin.DeprecationLevel.ERROR,
    replaceWith = kotlin.ReplaceWith("RoutingApplicationRequest", "io.ktor.server.routing.*")
)
public class RoutingApplicationRequest(
    call: RoutingApplicationCall,
    pipeline: ApplicationReceivePipeline,
    request: ApplicationRequest
)

@Deprecated(
    message = "Moved to io.ktor.server.routing",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("RoutingApplicationResponse", "io.ktor.server.routing.*")
)
public class RoutingApplicationResponse(
    call: RoutingApplicationCall,
    pipeline: ApplicationSendPipeline,
    response: ApplicationResponse
) : ApplicationResponse by response
