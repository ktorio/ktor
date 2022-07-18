/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.server.plugins.statuspages

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.application.hooks.*
import io.ktor.server.logging.*
import io.ktor.util.*
import io.ktor.util.reflect.*
import kotlin.jvm.*
import kotlin.reflect.*

/**
 * Specifies how the exception should be handled.
 */
public typealias HandlerFunction = suspend (call: ApplicationCall, cause: Throwable) -> Unit

/**
 * A plugin that handles exceptions and status codes. Useful to configure default error pages.
 */
public val StatusPages: ApplicationPlugin<StatusPagesConfig> = createApplicationPlugin(
    "StatusPages",
    ::StatusPagesConfig
) {
    val statusPageMarker = AttributeKey<Unit>("StatusPagesTriggered")

    val exceptions = HashMap(pluginConfig.exceptions)
    val statuses = HashMap(pluginConfig.statuses)

    fun findHandlerByValue(cause: Throwable): HandlerFunction? {
        val keys = exceptions.keys.filter { cause.instanceOf(it) }
        if (keys.isEmpty()) return null

        if (keys.size == 1) {
            return exceptions[keys.single()]
        }

        val key = selectNearestParentClass(cause, keys)
        return exceptions[key]
    }

    on(ResponseBodyReadyForSend) { call, content ->
        if (call.attributes.contains(statusPageMarker)) return@on

        val status = content.status ?: call.response.status() ?: return@on
        val handler = statuses[status] ?: return@on
        call.attributes.put(statusPageMarker, Unit)
        try {
            handler(call, content, status)
        } catch (cause: Throwable) {
            call.attributes.remove(statusPageMarker)
            throw cause
        }
    }

    on(CallFailed) { call, cause ->
        if (call.attributes.contains(statusPageMarker)) return@on

        val handler = findHandlerByValue(cause)
        handler ?: throw cause

        call.attributes.put(statusPageMarker, Unit)
        call.application.mdcProvider.withMDCBlock(call) {
            handler(call, cause)
        }

        if (!call.isHandled) {
            throw cause
        }
    }
}

/**
 * A [StatusPages] plugin configuration.
 */
@KtorDsl
public class StatusPagesConfig {
    /**
     * Provides access to exception handlers of the exception class.
     */
    public val exceptions: MutableMap<KClass<*>, HandlerFunction> = mutableMapOf()

    /**
     * Provides access to status handlers based on a status code.
     */
    public val statuses: MutableMap<HttpStatusCode,
        suspend (call: ApplicationCall, content: OutgoingContent, code: HttpStatusCode) -> Unit> =
        mutableMapOf()

    /**
     * Register an exception [handler] for the exception type [T] and its children.
     */
    public inline fun <reified T : Throwable> exception(
        noinline handler: suspend (call: ApplicationCall, cause: T) -> Unit
    ): Unit = exception(T::class, handler)

    /**
     * Register an exception [handler] for the exception class [klass] and its children.
     */
    public fun <T : Throwable> exception(
        klass: KClass<T>,
        handler: suspend (call: ApplicationCall, T) -> Unit
    ) {
        @Suppress("UNCHECKED_CAST")
        val cast = handler as suspend (ApplicationCall, Throwable) -> Unit

        exceptions[klass] = cast
    }

    /**
     * Register a status [handler] for the [status] code.
     */
    public fun status(
        vararg status: HttpStatusCode,
        handler: suspend (ApplicationCall, HttpStatusCode) -> Unit
    ) {
        status.forEach {
            statuses[it] = { call, _, code -> handler(call, code) }
        }
    }

    /**
     * Register a status [handler] for the [status] code.
     */
    @JvmName("statusWithContext")
    public fun status(
        vararg status: HttpStatusCode,
        handler: suspend StatusContext.(HttpStatusCode) -> Unit
    ) {
        status.forEach {
            statuses[it] = { call, content, code -> handler(StatusContext(call, content), code) }
        }
    }

    /**
     * A context for [status] config method.
     */
    public class StatusContext(
        public val call: ApplicationCall,
        public val content: OutgoingContent
    )
}
