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
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.sse.sse)
 *
 * @param path A URL path at which to handle Server-Sent Events (SSE) requests.
 * @param handler A function that defines the behavior of the SSE session. It is invoked when a client connects to the SSE
 * endpoint. Inside the handler, you can use the functions provided by [ServerSSESessionWithSerialization]
 * to send events to the connected clients.
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
 *
 * To learn more, see [the SSE](https://en.wikipedia.org/wiki/Server-sent_events)
 * and [the SSE specification](https://html.spec.whatwg.org/multipage/server-sent-events.html).
 *
 * @see ServerSSESession
 */
public fun Route.sse(path: String, handler: suspend ServerSSESession.() -> Unit) {
    route(path, HttpMethod.Get) {
        sse(handler)
    }
}

/**
 * Adds a route to handle Server-Sent Events (SSE) using the provided [handler].
 * Requires [SSE] plugin to be installed.
 *
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.sse.sse)
 *
 * @param handler A function that defines the behavior of the SSE session. It is invoked when a client connects to the SSE
 * endpoint. Inside the handler, you can use the functions provided by [ServerSSESessionWithSerialization]
 * to send events to the connected clients.
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
 *
 * To learn more, see [the SSE](https://en.wikipedia.org/wiki/Server-sent_events)
 * and [the SSE specification](https://html.spec.whatwg.org/multipage/server-sent-events.html).
 *
 * @see ServerSSESession
 */
public fun Route.sse(handler: suspend ServerSSESession.() -> Unit): Unit = processSSEWithoutSerialization(handler)

/**
 * Adds a route to handle Server-Sent Events (SSE) at the specified [path] using the provided [handler].
 * Requires [SSE] plugin to be installed.
 *
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.sse.sse)
 *
 * @param path A URL path at which to handle Server-Sent Events (SSE) requests.
 * @param serialize A function to serialize data objects into the `data` field of a `ServerSentEvent`.
 * @param handler A function that defines the behavior of the SSE session. It is invoked when a client connects to the SSE
 * endpoint. Inside the handler, you can use the functions provided by [ServerSSESessionWithSerialization]
 * to send events to the connected clients.
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
 *
 * To learn more, see [the SSE](https://en.wikipedia.org/wiki/Server-sent_events)
 * and [the SSE specification](https://html.spec.whatwg.org/multipage/server-sent-events.html).
 *
 * @see ServerSSESessionWithSerialization
 */
public fun Route.sse(
    path: String,
    serialize: (TypeInfo, Any) -> String,
    handler: suspend ServerSSESessionWithSerialization.() -> Unit
) {
    route(path, HttpMethod.Get) {
        sse(serialize, handler)
    }
}

/**
 * Adds a route to handle Server-Sent Events (SSE) using the provided [handler].
 * Requires [SSE] plugin to be installed.
 *
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.sse.sse)
 *
 * @param serialize A function to serialize data objects into the `data` field of a `ServerSentEvent`.
 * @param handler A function that defines the behavior of the SSE session. It is invoked when a client connects to the SSE
 * endpoint. Inside the handler, you can use the functions provided by [ServerSSESessionWithSerialization]
 * to send events to the connected clients.
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
 *
 * To learn more, see [the SSE](https://en.wikipedia.org/wiki/Server-sent_events)
 * and [the SSE specification](https://html.spec.whatwg.org/multipage/server-sent-events.html).
 *
 * @see ServerSSESessionWithSerialization
 */
public fun Route.sse(
    serialize: (TypeInfo, Any) -> String,
    handler: suspend ServerSSESessionWithSerialization.() -> Unit
): Unit = processSSEWithSerialization(serialize, handler)

private fun Route.processSSEWithoutSerialization(
    handler: suspend ServerSSESession.() -> Unit
) = processSSE(null, handler)

private fun Route.processSSEWithSerialization(
    serialize: ((TypeInfo, Any) -> String),
    handler: suspend ServerSSESessionWithSerialization.() -> Unit
) {
    val sessionHandler: suspend ServerSSESession.() -> Unit = {
        check(this is ServerSSESessionWithSerialization) {
            "Impossible state. Please report this bug: https://youtrack.jetbrains.com/newIssue?project=KTOR"
        }
        handler()
    }
    processSSE(serialize, sessionHandler)
}

private fun Route.processSSE(
    serialize: ((TypeInfo, Any) -> String)?,
    handler: suspend ServerSSESession.() -> Unit
) {
    plugin(SSE)

    handle {
        call.response.header(HttpHeaders.ContentType, ContentType.Text.EventStream.toString())
        call.response.header(HttpHeaders.CacheControl, "no-store")
        call.response.header(HttpHeaders.Connection, "keep-alive")
        call.response.header("X-Accel-Buffering", "no")
        call.respond(SSEServerContent(call, handler, serialize))
    }
}
