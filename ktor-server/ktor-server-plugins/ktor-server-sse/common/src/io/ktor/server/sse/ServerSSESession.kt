/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.sse

import io.ktor.server.application.*
import io.ktor.sse.*
import io.ktor.util.*
import io.ktor.util.reflect.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

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
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.sse.ServerSSESession)
 *
 * @see SSE
 */
public interface ServerSSESession : CoroutineScope {
    /**
     * The received [call] that originated this session.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.sse.ServerSSESession.call)
     */
    public val call: ApplicationCall

    /**
     * Sends a [ServerSentEvent] to the client.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.sse.ServerSSESession.send)
     */
    public suspend fun send(event: ServerSentEvent)

    /**
     * Creates and sends a [ServerSentEvent] to the client.
     *
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.sse.ServerSSESession.send)
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
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.sse.ServerSSESession.close)
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
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.sse.ServerSSESessionWithSerialization)
 *
 * @see SSE
 */
public interface ServerSSESessionWithSerialization : ServerSSESession {
    /**
     * Serializer for transforming data object into field `data` of `ServerSentEvent`.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.sse.ServerSSESessionWithSerialization.serializer)
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

/**
 * Starts a heartbeat for the ServerSSESession.
 *
 * The heartbeat will send the specified [Heartbeat.event] at the specified [Heartbeat.period] interval
 * as long as the session is active.
 *
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.sse.heartbeat)
 *
 * @param heartbeatConfig a lambda that configures the [Heartbeat] object used for the heartbeat.
 */
public fun ServerSSESession.heartbeat(heartbeatConfig: Heartbeat.() -> Unit = {}) {
    val heartbeat = Heartbeat().apply(heartbeatConfig)
    val heartbeatJob = Job(call.coroutineContext[Job])
    launch(heartbeatJob + CoroutineName("sse-heartbeat")) {
        while (true) {
            send(heartbeat.event)
            delay(heartbeat.period)
        }
    }
    call.attributes.put(heartbeatJobKey, heartbeatJob)
}

internal val heartbeatJobKey = AttributeKey<Job>("HeartbeatJobAttributeKey")

/**
 * Represents a heartbeat configuration for a [ServerSSESession].
 *
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.sse.Heartbeat)
 *
 * @property period the duration between heartbeat events, default is 30 seconds.
 * @property event the [ServerSentEvent] to be sent as the heartbeat, default is a [ServerSentEvent] with the comment "heartbeat".
 */
public class Heartbeat {
    public var period: Duration = 30.seconds
    public var event: ServerSentEvent = ServerSentEvent(comments = "heartbeat")
}
