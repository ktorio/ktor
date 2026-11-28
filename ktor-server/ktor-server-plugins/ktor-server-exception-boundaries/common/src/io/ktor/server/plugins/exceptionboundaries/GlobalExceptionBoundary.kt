/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.exceptionboundaries

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.*
import io.ktor.utils.io.*
import kotlinx.coroutines.coroutineScope
import kotlin.concurrent.Volatile
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.cancellation.CancellationException
import kotlin.reflect.KClass

/**
 * Registers the application-wide exception handlers.
 *
 * ```kotlin
 * globalExceptionBoundary {
 *     on<SQLTransientConnectionException> { call.respond(HttpStatusCode.ServiceUnavailable) }
 *     onApplicationCoroutineFailure<SQLException> { e, context -> alerts.databaseDown(context[CoroutineName], e) }
 * }
 * ```
 *
 * - Call handlers get the failures of plugins before routing, of routing, and of routes that no
 *   [exceptionBoundary] handled. On a running server they also get the failures of children of the call,
 *   such as `call.launch`.
 * - Coroutine handlers get the failures of `application.launch`, of plugin coroutines on the application scope,
 *   and of coroutines that the engine roots.
 * - A call exception that no handler matches is logged, and the call gets the default response for it,
 *   for example a 400 for `BadRequestException`, or a 500 with no body.
 * - A coroutine failure that no handler matches goes to the handler from `parentCoroutineContext`, or is logged.
 *
 * Calling this function again adds the new handlers after the existing ones.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.exceptionboundaries.globalExceptionBoundary)
 *
 * @param skipSentCalls when `true`, a call handler of this block doesn't run for an exception thrown after
 * the response was sent. The exception is logged instead.
 * @param build registers the handlers.
 * @throws IllegalArgumentException if a handler is registered for [Throwable] or `CancellationException`.
 */
public fun Application.globalExceptionBoundary(
    skipSentCalls: Boolean = true,
    build: GlobalExceptionBoundaryBuilder.() -> Unit,
) {
    val builder = GlobalExceptionBoundaryBuilder(skipSentCalls).apply(build)
    installGlobalExceptionBoundary().add(builder.callHandlers, builder.coroutineHandlers)
}

/**
 * Registers the handlers of [globalExceptionBoundary].
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.exceptionboundaries.GlobalExceptionBoundaryBuilder)
 */
@KtorDsl
public class GlobalExceptionBoundaryBuilder internal constructor(private val skipSentCalls: Boolean) {
    internal val callHandlers = mutableListOf<CallHandlerEntry>()
    internal val coroutineHandlers = mutableListOf<CoroutineHandlerEntry>()

    /**
     * Handles an exception of type [T], or of its subtypes, thrown while any call is processed.
     *
     * The handler gets a [RoutingContext] even when the call failed before routing.
     * Such a call belongs to the root route and has no path parameters.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.exceptionboundaries.GlobalExceptionBoundaryBuilder.on)
     *
     * @param handle responds to the call; it gets the exception as the argument.
     * @throws IllegalArgumentException if [T] is [Throwable] or `CancellationException`.
     */
    public inline fun <reified T : Throwable> on(noinline handle: suspend RoutingContext.(T) -> Unit) {
        on(T::class, handle)
    }

    /**
     * Handles a failure of type [T], or of its subtypes, in a coroutine rooted in the application or in its engine.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.exceptionboundaries.GlobalExceptionBoundaryBuilder.onApplicationCoroutineFailure)
     *
     * @param handle gets the failure and the context of the failed coroutine. It can't suspend.
     * @throws IllegalArgumentException if [T] is [Throwable] or `CancellationException`.
     */
    public inline fun <reified T : Throwable> onApplicationCoroutineFailure(
        noinline handle: (exception: T, context: CoroutineContext) -> Unit
    ) {
        onApplicationCoroutineFailure(T::class, handle)
    }

    @PublishedApi
    internal fun <T : Throwable> on(type: KClass<T>, handle: suspend RoutingContext.(T) -> Unit) {
        callHandlers += CallHandlerEntry(type, skipSentCalls, handle, function = "on")
    }

    @PublishedApi
    internal fun <T : Throwable> onApplicationCoroutineFailure(
        type: KClass<T>,
        handle: (T, CoroutineContext) -> Unit
    ) {
        coroutineHandlers += CoroutineHandlerEntry(type, handle, function = "onApplicationCoroutineFailure")
    }
}

private val GlobalExceptionBoundaryKey = AttributeKey<GlobalExceptionBoundaryRegistry>("GlobalExceptionBoundary")

/**
 * The handlers of every [globalExceptionBoundary] block of an application, in the order they were added.
 */
internal class GlobalExceptionBoundaryRegistry {
    private val allCallHandlers = mutableListOf<CallHandlerEntry>()
    private val allCoroutineHandlers = mutableListOf<CoroutineHandlerEntry>()

    @Volatile
    var callHandlers: HandlerTable<CallHandlerEntry> = HandlerTable(emptyList())
        private set

    @Volatile
    var coroutineHandlers: HandlerTable<CoroutineHandlerEntry> = HandlerTable(emptyList())
        private set

    fun add(callHandlers: List<CallHandlerEntry>, coroutineHandlers: List<CoroutineHandlerEntry>) {
        allCallHandlers += callHandlers
        allCoroutineHandlers += coroutineHandlers
        this.callHandlers = HandlerTable(allCallHandlers.toList())
        this.coroutineHandlers = HandlerTable(allCoroutineHandlers.toList())
    }

    fun handleCoroutineFailure(context: CoroutineContext, exception: Throwable): Boolean {
        val entry = coroutineHandlers.find(exception) ?: return false
        entry.handle(exception, context)
        return true
    }
}

@OptIn(InternalAPI::class)
internal fun Application.installGlobalExceptionBoundary(): GlobalExceptionBoundaryRegistry {
    attributes.getOrNull(GlobalExceptionBoundaryKey)?.let { return it }

    val registry = GlobalExceptionBoundaryRegistry()
    attributes.put(GlobalExceptionBoundaryKey, registry)
    setCoroutineFailureHandler(registry::handleCoroutineFailure)
    install(GlobalExceptionBoundaryPlugin) {
        this.registry = registry
    }
    return registry
}

internal class GlobalExceptionBoundaryConfig {
    lateinit var registry: GlobalExceptionBoundaryRegistry
}

internal val GlobalExceptionBoundaryPlugin =
    createApplicationPlugin("GlobalExceptionBoundary", ::GlobalExceptionBoundaryConfig) {
        val registry = pluginConfig.registry

        on(ExceptionBoundaryHook) { call, proceed ->
            try {
                // The call's coroutine context is captured inside this scope, so a failed call.launch rethrows here
                coroutineScope { proceed() }
            } catch (cause: Throwable) {
                // A scoped boundary already logged it
                if (call.isSkipped(cause)) return@on
                if (registry.callHandlers.handleCallFailure(call, cause)) return@on
                if (cause is CancellationException) throw cause
                respondUnhandled(call, cause)
            }
        }
    }

private suspend fun respondUnhandled(call: PipelineCall, cause: Throwable) {
    logError(call, cause)
    if (call.response.isCommitted || call.response.isSent) return

    val status = defaultExceptionStatusCode(cause) ?: HttpStatusCode.InternalServerError
    val message = cause.message.takeIf { status != HttpStatusCode.InternalServerError }
    try {
        when (message) {
            null -> call.respond(status)
            else -> call.respond(status, message)
        }
    } catch (_: BaseApplicationResponse.ResponseAlreadySentException) {
    }
}
