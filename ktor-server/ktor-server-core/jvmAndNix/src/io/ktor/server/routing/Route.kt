/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.routing

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.util.*
import io.ktor.util.pipeline.*
import io.ktor.util.reflect.*
import kotlinx.coroutines.*
import kotlin.coroutines.*

/**
 * Describes a node in a routing tree.
 * @see [Application.routing]
 *
 * @param parent is a parent node in the tree, or null for root node.
 * @param selector is an instance of [RouteSelector] for this node.
 * @param developmentMode is flag to switch report level for stack traces.
 */
@Suppress("DEPRECATION")
@KtorDsl
public open class Route(
    public override val parent: Route?,
    public val selector: RouteSelector,
    developmentMode: Boolean = false,
    environment: ApplicationEnvironment
) : ApplicationCallPipeline(developmentMode, environment), RoutingBuilder {

    /**
     * Describes a node in a routing tree.
     *
     * @param parent is a parent node in the tree, or null for root node.
     * @param selector is an instance of [RouteSelector] for this node.
     */
    @Deprecated(message = "Please use constructor with developmentMode parameter", level = DeprecationLevel.HIDDEN)
    public constructor(
        parent: Route?,
        selector: RouteSelector,
        environment: ApplicationEnvironment
    ) : this(parent, selector, developmentMode = false, environment = environment)

    /**
     * List of child routes for this node.
     */
    public val children: List<Route> get() = childList

    private val childList: MutableList<Route> = mutableListOf()

    private var cachedPipeline: ApplicationCallPipeline? = null

    internal val handlers = mutableListOf<RoutingHandler>()

    /**
     * Creates a child node in this node with a given [selector] or returns an existing one with the same selector.
     */
    public override fun createChild(selector: RouteSelector): Route {
        val existingEntry = childList.firstOrNull { it.selector == selector }
        if (existingEntry == null) {
            val entry = Route(this, selector, developmentMode, environment)
            childList.add(entry)
            return entry
        }
        return existingEntry
    }

    /**
     * Allows using a route instance for building additional routes.
     */
    public operator fun invoke(body: Route.() -> Unit): Unit = body()

    /**
     * Installs a handler into this route which is called when the route is selected for a call.
     */
    public override fun handle(body: RoutingHandler) {
        handlers.add(body)

        // Adding a handler invalidates only pipeline for this entry
        cachedPipeline = null
    }

    override fun <F : Any> plugin(plugin: Plugin<*, *, F>): F = (this as ApplicationCallPipeline).plugin(plugin)

    override fun <B : Any, F : Any> install(
        plugin: Plugin<ApplicationCallPipeline, B, F>,
        configure: B.() -> Unit
    ): F = (this as ApplicationCallPipeline).install(plugin, configure)

    override fun afterIntercepted() {
        // Adding an interceptor invalidates pipelines for all children
        // We don't need synchronisation here, because order of intercepting and acquiring pipeline is indeterminate
        // If some child already cached its pipeline, it's ok to execute with outdated pipeline
        invalidateCachesRecursively()
    }

    private fun invalidateCachesRecursively() {
        cachedPipeline = null
        childList.forEach { it.invalidateCachesRecursively() }
    }

    internal fun buildPipeline(): ApplicationCallPipeline = cachedPipeline ?: run {
        var current: Route? = this
        val pipeline = ApplicationCallPipeline(developmentMode, application.environment)
        val routePipelines = mutableListOf<ApplicationCallPipeline>()
        while (current != null) {
            routePipelines.add(current)
            current = current.parent
        }

        for (index in routePipelines.lastIndex downTo 0) {
            val routePipeline = routePipelines[index]
            pipeline.merge(routePipeline)
            pipeline.receivePipeline.merge(routePipeline.receivePipeline)
            pipeline.sendPipeline.merge(routePipeline.sendPipeline)
        }

        val handlers = handlers
        for (index in 0..handlers.lastIndex) {
            pipeline.intercept(Call) {
                val call = call as RoutingApplicationCall
                val routingCall = call.routingCall()
                val routingContext = RoutingContext(routingCall, coroutineContext)
                if (call.isHandled) return@intercept
                handlers[index].invoke(routingContext)
            }
        }
        cachedPipeline = pipeline
        pipeline
    }

    override fun toString(): String {
        return when (val parentRoute = parent?.toString()) {
            null -> when (selector) {
                is TrailingSlashRouteSelector -> "/"
                else -> "/$selector"
            }

            else -> when (selector) {
                is TrailingSlashRouteSelector -> if (parentRoute.endsWith('/')) parentRoute else "$parentRoute/"
                else -> if (parentRoute.endsWith('/')) "$parentRoute$selector" else "$parentRoute/$selector"
            }
        }
    }
}

public class RoutingRequest internal constructor(
    public val pathVariables: Parameters,
    internal val request: ApplicationRequest,
    public override val call: RoutingCall
) : Request {

    public override val queryParameters: Parameters = request.queryParameters
    public override val rawQueryParameters: Parameters = request.rawQueryParameters
    public override val headers: Headers = request.headers
    public override val local: RequestConnectionPoint = request.local
    public override val cookies: RequestCookies = request.cookies
}

public class RoutingResponse internal constructor(
    public override val call: RoutingCall,
    internal val applicationResponse: ApplicationResponse
) : Response {

    override val isCommitted: Boolean
        get() = applicationResponse.isCommitted

    override val isSent: Boolean
        get() = applicationResponse.isSent

    public override val headers: ResponseHeaders = applicationResponse.headers

    public override val cookies: ResponseCookies = applicationResponse.cookies

    override fun status(): HttpStatusCode? = applicationResponse.status()

    override fun status(value: HttpStatusCode) {
        applicationResponse.status(value)
    }

    @UseHttp2Push
    override fun push(builder: ResponsePushBuilder): Unit = applicationResponse.push(builder)
}

public class RoutingCall internal constructor(
    public val applicationCall: RoutingApplicationCall
) : Call {

    public override lateinit var request: RoutingRequest
        internal set
    public override lateinit var response: RoutingResponse
        internal set

    public override val attributes: Attributes = applicationCall.attributes
    public override val application: Application = applicationCall.application
    public override val parameters: Parameters = applicationCall.parameters
    public val pathParameters: Parameters = applicationCall.pathParameters
    public val queryParameters: Parameters = applicationCall.engineCall.parameters
    public val route: Route = applicationCall.route

    override suspend fun <T> receiveNullable(typeInfo: TypeInfo): T? = applicationCall.receiveNullable(typeInfo)

    override suspend fun respond(message: Any?, typeInfo: TypeInfo?) {
        applicationCall.respond(message, typeInfo)
    }
}

public class RoutingContext(
    public val call: RoutingCall,
    override val coroutineContext: CoroutineContext
) : CoroutineScope {
    public val application: Application
        get() = call.application
}

public typealias RoutingHandler = suspend RoutingContext.() -> Unit

public interface RoutingBuilder {
    public val environment: ApplicationEnvironment
    public val attributes: Attributes
    public val parent: RoutingBuilder?
    public fun handle(body: RoutingHandler)
    public fun createChild(selector: RouteSelector): RoutingBuilder

    /**
     * Gets a plugin instance for this pipeline, or fails with [MissingApplicationPluginException]
     * if the plugin is not installed.
     * @throws MissingApplicationPluginException
     * @param plugin [Plugin] to lookup
     * @return an instance of a plugin
     */
    public fun <F : Any> plugin(plugin: Plugin<*, *, F>): F

    /**
     * Installs a [plugin] into this route, if it is not yet installed.
     */
    public fun <B : Any, F : Any> install(
        plugin: Plugin<ApplicationCallPipeline, B, F>,
        configure: B.() -> Unit = {}
    ): F
}

public interface RootRoutingBuilder : RoutingBuilder {

    /**
     * Registers a function used to trace route resolution.
     * Might be useful if you need to understand why a route isn't executed.
     * To learn more, see [Tracing routes](https://ktor.io/docs/tracing-routes.html).
     */
    public fun trace(block: (RoutingResolveTrace) -> Unit)
}

private fun RoutingApplicationCall.routingCall(): RoutingCall {
    val call = RoutingCall(
        applicationCall = this
    )
    call.request = RoutingRequest(
        pathVariables = call.pathParameters,
        request = request,
        call = call
    )
    call.response = RoutingResponse(
        applicationResponse = response,
        call = call
    )
    return call
}

/**
 * Return list of endpoints with handlers under this route.
 */
public fun Route.getAllRoutes(): List<Route> {
    val endpoints = mutableListOf<Route>()
    getAllRoutes(endpoints)
    return endpoints
}

private fun Route.getAllRoutes(endpoints: MutableList<Route>) {
    if (handlers.isNotEmpty()) {
        endpoints.add(this)
    }
    children.forEach { it.getAllRoutes(endpoints) }
}

@Deprecated("Please use route scoped plugins instead")
public fun RoutingBuilder.intercept(
    phase: PipelinePhase,
    block: suspend PipelineContext<Unit, ApplicationCall>.(Unit) -> Unit
) {
    (this as Route).intercept(phase, block)
}

@Deprecated("Please use route scoped plugins instead")
public fun RoutingBuilder.insertPhaseAfter(reference: PipelinePhase, phase: PipelinePhase) {
    (this as Route).insertPhaseAfter(reference, phase)
}

@Deprecated("Please use route scoped plugins instead")
public fun RoutingBuilder.insertPhaseBefore(reference: PipelinePhase, phase: PipelinePhase) {
    (this as Route).insertPhaseBefore(reference, phase)
}
