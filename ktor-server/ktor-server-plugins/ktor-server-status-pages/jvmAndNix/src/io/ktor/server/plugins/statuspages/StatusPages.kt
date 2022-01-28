/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.server.plugins.statuspages

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.util.*
import io.ktor.util.reflect.*
import kotlin.reflect.*

/**
 * Specifies how the exception should be handled.
 */
public typealias HandlerFunction = suspend (call: ApplicationCall, cause: Throwable) -> Unit

/**
 * A plugin that handles exceptions and status codes. Useful to configure default error pages.
 */
public val StatusPages: ApplicationPlugin<Application, StatusPagesConfig, PluginInstance> = createApplicationPlugin(
    "StatusPages",
    { StatusPagesConfig() }
) {
    val statusPageMarker = AttributeKey<Unit>("StatusPagesTriggered")

    val exceptions = HashMap(pluginConfig.exceptions)
    val statuses = HashMap(pluginConfig.statuses)

    fun findHandlerByValue(cause: Throwable): HandlerFunction? {
        val key = exceptions.keys.find { cause.instanceOf(it) } ?: return null
        return exceptions[key]
    }

    onCallRespond.afterTransform { call, body ->
        if (call.attributes.contains(statusPageMarker)) return@afterTransform

        val status = when (body) {
            is OutgoingContent -> body.status
            is HttpStatusCode -> body
            else -> null
        } ?: return@afterTransform

        val handler = statuses[status] ?: return@afterTransform
        call.attributes.put(statusPageMarker, Unit)
        try {
            handler(call, status)
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
        handler(call, cause)

        if (!call.isHandled) {
            throw cause
        }
    }
}

/**
 * A [StatusPages] plugin configuration.
 */
public class StatusPagesConfig {
    /**
     * Provides access to exception handlers of the exception class.
     */
    public val exceptions: MutableMap<KClass<*>, HandlerFunction> = mutableMapOf()

    /**
     * Provides access to status handlers based on a status code.
     */
    public val statuses: MutableMap<HttpStatusCode, suspend (call: ApplicationCall, code: HttpStatusCode) -> Unit> =
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
            statuses[it] = handler
        }
    }
}
