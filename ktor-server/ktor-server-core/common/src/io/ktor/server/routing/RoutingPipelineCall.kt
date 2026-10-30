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

    // The call as its handlers see it, made once for routing and every handler of the route
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
    // Routing passes the application's own pipeline when the route adds nothing to it and the engine call made no copy
    // of it: this call's pipeline is then the engine call's, made the first time someone asks for it.
    private val routePipeline = pipeline.takeUnless { it === call.application.receivePipeline }

    override val pipeline: ApplicationReceivePipeline
        get() = routePipeline ?: engineRequest.pipeline

    internal val pipelineToRun: ApplicationReceivePipeline
        get() = routePipeline ?: engineRequest.pipelineToRun()
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
    // As RoutingPipelineRequest's: the engine call's pipeline unless the route adds to the application's
    private val routePipeline = pipeline.takeUnless { it === call.application.sendPipeline }

    override val pipeline: ApplicationSendPipeline
        get() = routePipeline ?: engineResponse.pipeline

    internal val pipelineToRun: ApplicationSendPipeline
        get() = routePipeline ?: engineResponse.pipelineToRun()
}
