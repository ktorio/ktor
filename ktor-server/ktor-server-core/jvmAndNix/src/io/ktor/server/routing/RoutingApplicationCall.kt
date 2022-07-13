/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.routing

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.util.*
import kotlinx.coroutines.*
import kotlin.coroutines.*

/**
 * An application call handled by [Routing].
 * @property route is the selected route
 */
public class RoutingApplicationCall(
    internal val call: ApplicationCall,
    public val route: Route,
    override val coroutineContext: CoroutineContext,
    receivePipeline: ApplicationReceivePipeline,
    responsePipeline: ApplicationSendPipeline,
    internal val pathParameters: Parameters
) : ApplicationCall, CoroutineScope {

    override val application: Application get() = call.application
    override val attributes: Attributes get() = call.attributes

    override val request: RoutingApplicationRequest = RoutingApplicationRequest(this, receivePipeline, call.request)

    override val response: RoutingApplicationResponse =
        RoutingApplicationResponse(this, responsePipeline, call.response)

    override val parameters: Parameters by lazy(LazyThreadSafetyMode.NONE) {
        Parameters.build {
            appendAll(call.parameters)
            appendMissing(pathParameters)
        }
    }

    override fun toString(): String = "RoutingApplicationCall(route=$route)"
}

/**
 * An application request handled by [Routing].
 */
public class RoutingApplicationRequest(
    override val call: RoutingApplicationCall,
    override val pipeline: ApplicationReceivePipeline,
    request: ApplicationRequest
) : ApplicationRequest by request

/**
 * An application response handled by [Routing].
 */
public class RoutingApplicationResponse(
    override val call: RoutingApplicationCall,
    override val pipeline: ApplicationSendPipeline,
    response: ApplicationResponse
) : ApplicationResponse by response
