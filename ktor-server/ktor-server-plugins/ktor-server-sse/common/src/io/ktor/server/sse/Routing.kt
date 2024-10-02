/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.sse

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

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
public fun <T : Any> Route.sse(path: String, handler: suspend SSESession<T>.() -> Unit) {
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
public fun <T : Any> Route.sse(handler: suspend SSESession<T>.() -> Unit) {
    val sse = application.plugin(SSE)

    handle {
        call.response.header(HttpHeaders.ContentType, ContentType.Text.EventStream.toString())
        call.response.header(HttpHeaders.CacheControl, "no-store")
        call.response.header(HttpHeaders.Connection, "keep-alive")
        call.response.header("X-Accel-Buffering", "no")
        call.respond(SSEServerContent(call, sse.serialize, handler))
    }
}

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
public fun <T> Route.sse(
    path: String,
    serialize: (T) -> String = { it.toString() },
    handler: suspend SSESession<T>.() -> Unit
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
 * endpoint. Inside the handler, you can use the functions provided by [SSESession]
 * to send events to the connected clients.
 *
 * @see SSESession
 */
public fun <T> Route.sse(serialize: (T) -> String = { it.toString() }, handler: suspend SSESession<T>.() -> Unit) {
    plugin(SSE)

    handle {
        call.response.header(HttpHeaders.ContentType, ContentType.Text.EventStream.toString())
        call.response.header(HttpHeaders.CacheControl, "no-store")
        call.response.header(HttpHeaders.Connection, "keep-alive")
        call.response.header("X-Accel-Buffering", "no")
        call.respond(SSEServerContent(call, serialize, handler))
    }
}
