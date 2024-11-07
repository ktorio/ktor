/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.sse

import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.reflect.*

/**
 * Adds a route to handle Server-Sent Events (SSE) at the specified [path] using the provided [handler].
 * Requires [SSE] plugin to be installed.
 *
 * @param path URL path at which to handle SSE requests.
 * @param handler function that defines the behavior of the SSE session. It is invoked when a client connects to the SSE
 * endpoint. Inside the handler, you can use the functions provided by [SSESession]
 * to send events to the connected clients.
 *
 * @see SSESession
 *
 * Example of usage:
 * ```kotlin
 * install(SSE)
 * routing {
 *     sse("/events") {
 *         repeat(100) {
 *             send(ServerSentEvent("event $it"))
 *         }
 *     }
 * }
 * ```
 */
public fun Route.sse(path: String, handler: suspend SSESession.() -> Unit) {
    route(path, HttpMethod.Get) {
        sse(handler)
    }
}

/**
 * Adds a route to handle Server-Sent Events (SSE) using the provided [handler].
 * Requires [SSE] plugin to be installed.
 *
 * @param handler function that defines the behavior of the SSE session. It is invoked when a client connects to the SSE
 * endpoint. Inside the handler, you can use the functions provided by [SSESession]
 * to send events to the connected clients.
 *
 * @see SSESession
 *
 * Example of usage:
 * ```kotlin
 * install(SSE)
 * routing {
 *     sse {
 *         repeat(100) {
 *             send(ServerSentEvent("event $it"))
 *         }
 *     }
 * }
 * ```
 */
public fun Route.sse(handler: suspend SSESession.() -> Unit): Unit = processSSE(null, handler)

/**
 * Adds a route to handle Server-Sent Events (SSE) at the specified [path] using the provided [handler].
 * Requires [SSE] plugin to be installed.
 *
 * @param path URL path at which to handle SSE requests.
 * @param serialize A function to serialize data objects into the `data` field of a `ServerSentEvent`.
 * @param handler function that defines the behavior of the SSE session. It is invoked when a client connects to the SSE
 * endpoint. Inside the handler, you can use the functions provided by [SSESessionWithSerialization]
 * to send events to the connected clients.
 *
 * @see SSESessionWithSerialization
 *
 * Example of usage:
 * ```kotlin
 * install(SSE)
 * routing {
 *     sse("/json", serialize = { typeInfo, it ->
 *         val serializer = Json.serializersModule.serializer(typeInfo.kotlinType!!)
 *         Json.encodeToString(serializer, it)
 *     }) {
 *         send(Customer(0, "Jet", "Brains"))
 *         send(Product(0, listOf(100, 200)))
 *     }
 * }
 * ```
 */
public fun Route.sse(
    path: String,
    serialize: (TypeInfo, Any) -> String = { _, it -> it.toString() },
    handler: suspend SSESessionWithSerialization.() -> Unit
) {
    route(path, HttpMethod.Get) {
        sse(serialize, handler)
    }
}

/**
 * Adds a route to handle Server-Sent Events (SSE) using the provided [handler].
 * Requires [SSE] plugin to be installed.
 *
 * @param serialize serialize function for transforming data object into field `data` of `ServerSentEvent`
 * @param handler function that defines the behavior of the SSE session. It is invoked when a client connects to the SSE
 * endpoint. Inside the handler, you can use the functions provided by [SSESessionWithSerialization]
 * to send events to the connected clients.
 *
 * @see SSESessionWithSerialization
 *
 * Example of usage:
 * ```kotlin
 * install(SSE)
 * routing {
 *     sse(serialize = { typeInfo, it ->
 *         val serializer = Json.serializersModule.serializer(typeInfo.kotlinType!!)
 *         Json.encodeToString(serializer, it)
 *     }) {
 *         send(Customer(0, "Jet", "Brains"))
 *         send(Product(0, listOf(100, 200)))
 *     }
 * }
 * ```
 */
public fun Route.sse(
    serialize: (TypeInfo, Any) -> String = { _, it -> it.toString() },
    handler: suspend SSESessionWithSerialization.() -> Unit
): Unit = processSSE(serialize, handler)

private fun Route.processSSE(
    serialize: ((TypeInfo, Any) -> String)?,
    handler: suspend SSESessionWithSerialization.() -> Unit
) {
    plugin(SSE)

    handle {
        call.response.header(HttpHeaders.ContentType, ContentType.Text.EventStream.toString())
        call.response.header(HttpHeaders.CacheControl, "no-store")
        call.response.header(HttpHeaders.Connection, "keep-alive")
        call.response.header("X-Accel-Buffering", "no")
        call.respond(SSEServerContent(call, serialize, handler))
    }
}
