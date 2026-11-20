/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.exceptionboundaries

import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.util.logging.*
import io.ktor.util.pipeline.*
import io.ktor.utils.io.*
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/**
 * Creates a route subtree whose calls are handled by [handlers] when they fail with an exception.
 *
 * ```kotlin
 * exceptionBoundary(dbFailures, httpFailures) {
 *     get("/users/{id}") { /* ... */ }
 * }
 * ```
 *
 * - The handlers are joined in the order given, and the first match wins.
 * - Nested boundaries are checked from the innermost outward.
 * - An exception that no boundary handles goes to [globalExceptionBoundary]. When that doesn't handle it either,
 *   the call gets the default response for the exception, or a 500 with no body.
 *
 * The global boundary plugin is installed when it is missing, so this fallback works without [globalExceptionBoundary].
 *
 * The boundary handles exceptions of the route's own coroutine, including children that fail it, for example
 * in `coroutineScope`. It doesn't handle a failed `call.launch`: that child belongs to the coroutine of the whole
 * call, which is outside every scoped boundary. On a running server the exception reaches [globalExceptionBoundary]
 * like any other exception of the call. In `testApplication`, no boundary gets it and the test receives it.
 * Such a failure is a logic error, so a scoped boundary isn't the place to report it to the client.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.exceptionboundaries.exceptionBoundary)
 *
 * @param handlers the handlers created with [exceptionHandler].
 * @param build builds the routes of the boundary.
 * @return the route of the boundary.
 * @throws IllegalArgumentException if no handlers are given.
 */
public fun Route.exceptionBoundary(vararg handlers: ExceptionHandler, build: Route.() -> Unit): Route {
    require(handlers.isNotEmpty()) { "exceptionBoundary needs at least one exception handler" }
    application.installGlobalExceptionBoundary()

    val route = createChild(ExceptionBoundaryRouteSelector())
    // Not a route-scoped plugin: a plugin installed in a nested route replaces the outer one instead of wrapping it
    check(route is ApplicationCallPipeline) { "exceptionBoundary needs a RoutingNode, but got ${route::class}" }
    route.interceptFailures(handlers.toList())
    route.build()
    return route
}

// Not a data class: two boundaries on the same parent must create two child routes
@OptIn(InternalAPI::class)
internal class ExceptionBoundaryRouteSelector : TransparentRouteSelector() {
    override fun toString(): String = "(exceptionBoundary)"
}

private fun ApplicationCallPipeline.interceptFailures(handlers: List<ExceptionHandler>) {
    val callHandlers = HandlerTable(handlers.flatMap { it.callHandlers })
    val coroutineHandlers = HandlerTable(handlers.flatMap { it.coroutineHandlers })

    ExceptionBoundaryHook.install(this) { call, proceed ->
        try {
            if (coroutineHandlers.isEmpty()) {
                proceed()
            } else {
                // Coroutines started from the route's own coroutine inherit this handler, call.launch doesn't
                val exceptionHandler = CallCoroutineExceptionHandler(
                    coroutineHandlers,
                    parent = currentCoroutineContext()[CoroutineExceptionHandler],
                    application = call.application
                )
                withContext(exceptionHandler) { proceed() }
            }
        } catch (cause: Throwable) {
            if (!callHandlers.handleCallFailure(call, cause)) throw cause
        }
    }
}

/**
 * Wraps the rest of the call processing, starting before the [ApplicationCallPipeline.Setup] phase.
 */
internal object ExceptionBoundaryHook : Hook<suspend (call: PipelineCall, proceed: suspend () -> Unit) -> Unit> {
    private val phase = PipelinePhase("ExceptionBoundary")

    override fun install(
        pipeline: ApplicationCallPipeline,
        handler: suspend (call: PipelineCall, proceed: suspend () -> Unit) -> Unit
    ) {
        pipeline.insertPhaseBefore(ApplicationCallPipeline.Setup, phase)
        pipeline.intercept(phase) {
            handler(call) { proceed() }
        }
    }
}

/**
 * Gets failures of coroutines that a call started without passing their failure to the call,
 * for example the children of `supervisorScope`.
 */
@OptIn(InternalAPI::class)
internal class CallCoroutineExceptionHandler(
    private val handlers: HandlerTable<CoroutineHandlerEntry>,
    private val parent: CoroutineExceptionHandler?,
    private val application: Application,
) : AbstractCoroutineContextElement(CoroutineExceptionHandler), CoroutineExceptionHandler {

    override fun handleException(context: CoroutineContext, exception: Throwable) {
        val entry = handlers.find(exception)
        if (entry != null) return entry.handle(exception, context)
        if (parent != null) return parent.handleException(context, exception)
        if (application.handleCoroutineFailure(context, exception)) return

        val coroutineName = context[CoroutineName] ?: context.toString()
        application.log.error("Unhandled exception caught for $coroutineName", exception)
    }
}
