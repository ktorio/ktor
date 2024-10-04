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
 */
public fun Route.sse(handler: suspend SSESession.() -> Unit): Unit = processSSE(null, handler)

/**
 * Adds a route to handle Server-Sent Events (SSE) at the specified [path] using the provided [handler].
 * Requires [SSE] plugin to be installed.
 *
 * @param path URL path at which to handle SSE requests.
 * @param handler function that defines the behavior of the SSE session. It is invoked when a client connects to the SSE
 * endpoint. Inside the handler, you can use the functions provided by [SSESessionWithDeserialization]
 * to send events to the connected clients.
 *
 * @see SSESessionWithDeserialization
 */
public fun Route.sse(
    path: String,
    serialize: (TypeInfo) -> (Any) -> String = { { it.toString() } },
    handler: suspend SSESessionWithDeserialization.() -> Unit
) {
    route(path, HttpMethod.Get) {
        sse(serialize, handler)
    }
}

/**
 * Adds a route to handle Server-Sent Events (SSE) using the provided [handler].
 * Requires [SSE] plugin to be installed.
 *
 * @param handler function that defines the behavior of the SSE session. It is invoked when a client connects to the SSE
 * endpoint. Inside the handler, you can use the functions provided by [SSESessionWithDeserialization]
 * to send events to the connected clients.
 *
 * @see SSESessionWithDeserialization
 */
public fun Route.sse(
    serialize: (TypeInfo) -> (Any) -> String = { { it.toString() } },
    handler: suspend SSESessionWithDeserialization.() -> Unit
): Unit = processSSE(serialize, handler)

private fun Route.processSSE(
    serialize: ((TypeInfo) -> (Any) -> String)?,
    handler: suspend SSESessionWithDeserialization.() -> Unit
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
