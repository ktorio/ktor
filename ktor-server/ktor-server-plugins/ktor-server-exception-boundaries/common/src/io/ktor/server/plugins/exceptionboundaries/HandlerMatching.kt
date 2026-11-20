/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.exceptionboundaries

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.routing.*
import io.ktor.util.*
import io.ktor.util.collections.*
import io.ktor.utils.io.*
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.cancellation.CancellationException
import kotlin.reflect.KClass

internal sealed class HandlerEntry(val type: KClass<out Throwable>, function: String) {
    init {
        require(type != Throwable::class && type != CancellationException::class) {
            "$function<${type.simpleName}> is not allowed: register a specific exception type instead of " +
                "Throwable or CancellationException"
        }
    }
}

internal class CallHandlerEntry(
    type: KClass<out Throwable>,
    val skipSentCalls: Boolean,
    handle: suspend RoutingContext.(Nothing) -> Unit,
    function: String,
) : HandlerEntry(type, function) {
    // Only called with an exception that matched type
    @Suppress("UNCHECKED_CAST")
    val handle = handle as suspend RoutingContext.(Throwable) -> Unit
}

internal class CoroutineHandlerEntry(
    type: KClass<out Throwable>,
    handle: (Nothing, CoroutineContext) -> Unit,
    function: String,
) : HandlerEntry(type, function) {
    // Only called with an exception that matched type
    @Suppress("UNCHECKED_CAST")
    val handle = handle as (Throwable, CoroutineContext) -> Unit
}

/**
 * Finds the first entry that matches an exception, and remembers the result for each exception class.
 */
internal class HandlerTable<E : HandlerEntry>(private val entries: List<E>) {
    private class Lookup<E>(val entry: E?)

    private val cache = ConcurrentMap<KClass<*>, Lookup<E>>()

    fun isEmpty(): Boolean = entries.isEmpty()

    fun find(cause: Throwable): E? = cache.computeIfAbsent(cause::class) {
        Lookup(entries.firstOrNull { it.type.isInstance(cause) })
    }.entry
}

/**
 * Marks an exception that was skipped because the call was already sent,
 * so the outer boundaries skip it too and it is logged only once.
 */
private val SkippedExceptionKey = AttributeKey<Throwable>("ExceptionBoundarySkippedException")

internal fun ApplicationCall.isSkipped(cause: Throwable): Boolean = attributes.getOrNull(SkippedExceptionKey) === cause

/**
 * Runs the call handler of [table] that matches [cause].
 *
 * @return `true` if a handler handled [cause], `false` if the caller should pass it on.
 */
@OptIn(InternalAPI::class)
internal suspend fun HandlerTable<CallHandlerEntry>.handleCallFailure(call: PipelineCall, cause: Throwable): Boolean {
    if (call.isSkipped(cause)) return false
    val entry = find(cause) ?: return false
    if (entry.skipSentCalls && call.response.isSent) {
        logError(call, cause)
        call.attributes.put(SkippedExceptionKey, cause)
        return false
    }
    // Building the context of a call that failed before routing can fail again, for example on a malformed query
    val context = runCatching { call.toRoutingContext() }.getOrElse { return false }
    entry.handle(context, cause)
    return true
}
