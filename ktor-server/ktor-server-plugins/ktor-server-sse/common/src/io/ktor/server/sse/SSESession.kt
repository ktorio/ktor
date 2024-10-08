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
 * Represents a server-side server-sent events session.
 * An [SSESession] allows the server to send [ServerSentEvent] to the client over a single HTTP connection.
 *
 * @see [SSE]
 */
public interface SSESession : CoroutineScope {
    /**
     * Associated received [call] that originating this session.
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
     * Closes the [SSESession], terminating the connection with the client.
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
 * Represents a server-side server-sent events session with serialization support.
 * An [SSESessionWithSerialization] allows the server to send [ServerSentEvent] to the client over a single HTTP connection.
 *
 * @see [SSE]
 */
public interface SSESessionWithSerialization : SSESession {
    /**
     * Serializer for transforming data object into field `data` of `ServerSentEvent`.
     */
    public val serializer: (TypeInfo) -> (Any) -> String
}

public suspend inline fun <reified T : Any> SSESessionWithSerialization.sendSerialized(
    event: ParameterizedServerSentEvent<T>
) {
    send(
        ServerSentEvent(
            event.data?.let {
                serializer(typeInfo<T>()).invoke(it)
            },
            event.event,
            event.id,
            event.retry,
            event.comments
        )
    )
}

public suspend inline fun <reified T : Any> SSESessionWithSerialization.sendSerialized(
    data: T? = null,
    event: String? = null,
    id: String? = null,
    retry: Long? = null,
    comments: String? = null
) {
    sendSerialized(ParameterizedServerSentEvent(data, event, id, retry, comments))
}

public suspend inline fun <reified T : Any> SSESessionWithSerialization.sendSerialized(data: T) {
    send(ServerSentEvent(serializer(typeInfo<T>()).invoke(data)))
}
