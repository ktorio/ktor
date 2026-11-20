/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.exceptionboundaries

import io.ktor.server.routing.*
import io.ktor.utils.io.*
import kotlin.coroutines.CoroutineContext
import kotlin.reflect.KClass

/**
 * A reusable set of exception handlers that [exceptionBoundary] applies to a route subtree.
 * Create one with [exceptionHandler].
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.exceptionboundaries.ExceptionHandler)
 */
public class ExceptionHandler internal constructor(
    internal val callHandlers: List<CallHandlerEntry>,
    internal val coroutineHandlers: List<CoroutineHandlerEntry>,
)

/**
 * Creates an [ExceptionHandler] that [exceptionBoundary] applies to a route subtree.
 *
 * ```kotlin
 * val dbFailures = exceptionHandler {
 *     on<SQLTransientConnectionException> { call.respond(HttpStatusCode.ServiceUnavailable) }
 *     on<SQLException> { call.respond(HttpStatusCode.InternalServerError) }
 * }
 * ```
 *
 * Handlers are matched by the class of the exception, including its supertypes and interfaces.
 * They are checked in declaration order, and the first match wins, so a specific type goes before a general one.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.exceptionboundaries.exceptionHandler)
 *
 * @param skipSentCalls when `true`, a call handler doesn't run for an exception thrown after the response was sent.
 * The exception is logged instead, and the outer boundaries don't get it either.
 * @param build registers the handlers.
 * @return the handler to pass to [exceptionBoundary].
 * @throws IllegalArgumentException if a handler is registered for [Throwable] or `CancellationException`.
 */
public fun exceptionHandler(
    skipSentCalls: Boolean = true,
    build: ExceptionHandlerBuilder.() -> Unit,
): ExceptionHandler {
    val builder = ExceptionHandlerBuilder(skipSentCalls).apply(build)
    return ExceptionHandler(builder.callHandlers.toList(), builder.coroutineHandlers.toList())
}

/**
 * Registers the handlers of an [ExceptionHandler].
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.exceptionboundaries.ExceptionHandlerBuilder)
 */
@KtorDsl
public class ExceptionHandlerBuilder internal constructor(private val skipSentCalls: Boolean) {
    internal val callHandlers = mutableListOf<CallHandlerEntry>()
    internal val coroutineHandlers = mutableListOf<CoroutineHandlerEntry>()

    /**
     * Handles an exception of type [T], or of its subtypes, thrown while a call of the boundary's routes is processed.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.exceptionboundaries.ExceptionHandlerBuilder.on)
     *
     * @param handle responds to the call; it gets the exception as the argument.
     * @throws IllegalArgumentException if [T] is [Throwable] or `CancellationException`.
     */
    public inline fun <reified T : Throwable> on(noinline handle: suspend RoutingContext.(T) -> Unit) {
        on(type = T::class, handle)
    }

    /**
     * Handles a failure of type [T], or of its subtypes, in a coroutine started by a call of the boundary's routes
     * that doesn't pass its failure to the call, for example a child of `supervisorScope`.
     *
     * A failure that no handler of the boundary matches goes to the outer boundaries, then to
     * [GlobalExceptionBoundaryBuilder.onApplicationCoroutineFailure], and is logged when nothing handles it.
     *
     * A failed `call.launch` doesn't come here: it fails the coroutine of the whole call (see [exceptionBoundary]).
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.exceptionboundaries.ExceptionHandlerBuilder.onCallCoroutineFailure)
     *
     * @param handle gets the failure and the context of the failed coroutine. It can't suspend.
     * @throws IllegalArgumentException if [T] is [Throwable] or `CancellationException`.
     */
    public inline fun <reified T : Throwable> onCallCoroutineFailure(
        noinline handle: (exception: T, context: CoroutineContext) -> Unit
    ) {
        onCallCoroutineFailure(T::class, handle)
    }

    @PublishedApi
    internal fun <T : Throwable> on(type: KClass<T>, handle: suspend RoutingContext.(T) -> Unit) {
        callHandlers += CallHandlerEntry(type, skipSentCalls, handle, function = "on")
    }

    @PublishedApi
    internal fun <T : Throwable> onCallCoroutineFailure(type: KClass<T>, handle: (T, CoroutineContext) -> Unit) {
        coroutineHandlers += CoroutineHandlerEntry(type, handle, function = "onCallCoroutineFailure")
    }
}
