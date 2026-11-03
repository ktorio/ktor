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
 * An application call handled by [RoutingRoot].
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.routing.RoutingPipelineCall)
 *
 * @property engineCall original call from [io.ktor.server.engine.ApplicationEngine]
 * @property route is the selected route
 */
public class RoutingPipelineCall(
    public val engineCall: PipelineCall,
    public val route: RoutingNode,
    override val coroutineContext: CoroutineContext,
    receivePipeline: ApplicationReceivePipeline,
    responsePipeline: ApplicationSendPipeline,
    public val pathParameters: Parameters
) : PipelineCall, CoroutineScope {
    @Deprecated(level = DeprecationLevel.WARNING, message = "Use explicit coroutineContext instead.")
    public constructor(
        engineCall: PipelineCall,
        route: RoutingNode,
        receivePipeline: ApplicationReceivePipeline,
        responsePipeline: ApplicationSendPipeline,
        pathParameters: Parameters
    ) : this(
        engineCall,
        route,
        engineCall.coroutineContext,
        receivePipeline,
        responsePipeline,
        pathParameters
    )

    override val application: Application get() = engineCall.application
    override val attributes: Attributes get() = engineCall.attributes

    override val request: RoutingPipelineRequest =
        RoutingPipelineRequest(this, receivePipeline, engineCall.request)

    override val response: RoutingPipelineResponse =
        RoutingPipelineResponse(this, responsePipeline, engineCall.response)

    private var allParameters: Parameters? = null

    override val parameters: Parameters
        get() = allParameters ?: Parameters.build {
            appendAll(engineCall.parameters)
            appendMissing(pathParameters)
        }.also { allParameters = it }

    private var handlerCall: RoutingCall? = null

    internal val routingCall: RoutingCall
        get() = handlerCall ?: RoutingCall(this).also { handlerCall = it }

    override fun toString(): String = "RoutingApplicationCall(route=$route)"
}

/**
 * An application request handled by [RoutingRoot].
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.routing.RoutingPipelineRequest)
 */
public class RoutingPipelineRequest(
    override val call: RoutingPipelineCall,
    pipeline: ApplicationReceivePipeline,
    public val engineRequest: PipelineRequest
) : PipelineRequest by engineRequest {
    // The application's pipeline stands for a route that adds nothing: the engine request's runs instead
    private val routePipeline = pipeline.takeUnless { it === call.application.receivePipeline }

    @Deprecated(
        "A call's own receive pipeline will be removed. Intercept the application's or a route's instead, " +
            "for example with onCallReceive in a plugin, and use execute() to run it."
    )
    @Suppress("DEPRECATION")
    override val pipeline: ApplicationReceivePipeline
        get() = routePipeline ?: engineRequest.pipeline

    override suspend fun execute(call: PipelineCall, subject: Any): Any =
        if (routePipeline != null) routePipeline.execute(call, subject) else engineRequest.execute(call, subject)
}

/**
 * An application response handled by [RoutingRoot].
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.routing.RoutingPipelineResponse)
 */
public class RoutingPipelineResponse(
    override val call: RoutingPipelineCall,
    pipeline: ApplicationSendPipeline,
    public val engineResponse: PipelineResponse
) : PipelineResponse by engineResponse {
    private val routePipeline = pipeline.takeUnless { it === call.application.sendPipeline }

    @Deprecated(
        "A call's own send pipeline will be removed. Intercept the application's or a route's instead, " +
            "for example with onCallRespond in a plugin, and use execute() to run it."
    )
    @Suppress("DEPRECATION")
    override val pipeline: ApplicationSendPipeline
        get() = routePipeline ?: engineResponse.pipeline

    override suspend fun execute(call: PipelineCall, subject: Any): Any =
        if (routePipeline != null) routePipeline.execute(call, subject) else engineResponse.execute(call, subject)
}
