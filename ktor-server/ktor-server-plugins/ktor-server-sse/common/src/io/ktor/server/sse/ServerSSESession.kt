/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.sse

import io.ktor.server.application.*
import io.ktor.sse.*
import io.ktor.util.reflect.*
import io.ktor.websocket.*
import kotlinx.coroutines.*

/**
 * Represents a server-side Server-Sent Events (SSE) session.
 * An [ServerSSESession] allows the server to send [ServerSentEvent] to the client over a single HTTP connection.
 *
 * Example of usage:
 * ```kotlin
 * install(SSE)
 * routing {
 *     sse("/default") {
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
 * @see SSE
 */
public interface ServerSSESession : CoroutineScope {
    /**
     * The received [call] that originated this session.
     */
    public val call: ApplicationCall

    /**
     * Sends a [ServerSentEvent] to the client.
     */
    public suspend fun send(event: ServerSentEvent)

    /**
     * Creates and sends a [ServerSentEvent] to the client.
     *
     *  @param data data field of the event.
     *  @param event string identifying the type of event.
     *  @param id event ID.
     *  @param retry reconnection time, in milliseconds to wait before reconnecting.
     *  @param comments comment lines starting with a ':' character.
     */
    public suspend fun send(
        data: String? = null,
        event: String? = null,
        id: String? = null,
        retry: Long? = null,
        comments: String? = null
    ) {
        send(ServerSentEvent(data, event, id, retry, comments))
    }

    /**
     * Closes the [ServerSSESession], terminating the connection with the client.
     * Once this method is called, the SSE session is closed and no further events can be sent.
     * You don't need to call this method as it is called automatically when all the send operations are completed.
     *
     * It's important to note that closing the session using this method does not send a termination event
     * to the client. If you wish to send a specific event to signify the end of the SSE stream
     * before closing the session, you can use the [send] function for it.
     */
    public suspend fun close()
}

/**
 * Represents a server-side Server-Sent Events (SSE) session with serialization support.
 * An [ServerSSESessionWithSerialization] allows the server to send [ServerSentEvent] to the client over a single HTTP connection.
 *
 * Example of usage:
 * ```kotlin
 * install(SSE)
 * routing {
 *     sse("/serialization", serialize = { typeInfo, it ->
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
 * @see SSE
 */
public interface ServerSSESessionWithSerialization : ServerSSESession {
    /**
     * Serializer for transforming data object into field `data` of `ServerSentEvent`.
     */
    public val serializer: (TypeInfo, Any) -> String
}

public suspend inline fun <reified T : Any> ServerSSESessionWithSerialization.send(event: TypedServerSentEvent<T>) {
    send(
        ServerSentEvent(
            event.data?.let {
                serializer(typeInfo<T>(), it)
            },
            event.event,
            event.id,
            event.retry,
            event.comments
        )
    )
}

public suspend inline fun <reified T : Any> ServerSSESessionWithSerialization.send(
    data: T? = null,
    event: String? = null,
    id: String? = null,
    retry: Long? = null,
    comments: String? = null
) {
    send(TypedServerSentEvent(data, event, id, retry, comments))
}

public suspend inline fun <reified T : Any> ServerSSESessionWithSerialization.send(data: T) {
    send(ServerSentEvent(serializer(typeInfo<T>(), data)))
}
