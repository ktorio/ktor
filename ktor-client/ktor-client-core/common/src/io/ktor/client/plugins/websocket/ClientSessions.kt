/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.plugins.websocket

import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.http.cio.websocket.*
import io.ktor.shared.serialization.*
import io.ktor.util.reflect.*
import io.ktor.utils.io.*

/**
 * Client specific [WebSocketSession].
 */
public interface ClientWebSocketSession : WebSocketSession {
    /**
     * [HttpClientCall] associated with session.
     */
    public val call: HttpClientCall
}

/**
 * ClientSpecific [DefaultWebSocketSession].
 */
public class DefaultClientWebSocketSession(
    override val call: HttpClientCall,
    delegate: DefaultWebSocketSession
) : ClientWebSocketSession, DefaultWebSocketSession by delegate

internal class DelegatingClientWebSocketSession(
    override val call: HttpClientCall,
    session: WebSocketSession
) : ClientWebSocketSession, WebSocketSession by session

/**
 *
 */
public suspend inline fun <reified T : Any> DefaultClientWebSocketSession.sendSerializedByWebsocketConverter(data: T) {
    val serializedData = call.client?.plugin(WebSockets)?.contentConverter?.serialize(
        charset = call.request.headers.suitableCharset(),
        typeInfo = typeInfo<T>(),
        value = data
    ) ?: throw WebsocketConverterNotFoundException("No converter was found for websocket")

    outgoing.send(Frame.Text(serializedData))
}

/**
 *
 */
public suspend inline fun <reified T: Any> DefaultClientWebSocketSession.receiveDeserialized(): T {
    val data = when(val frame = incoming.receive()) {
        is Frame.Text -> frame.data
        is Frame.Binary -> frame.data
        else -> throw WebsocketDeserializeException("Frame type is not Frame.Text or Frame.Binary")
    }

    val result = call.client?.plugin(WebSockets)?.contentConverter?.deserialize(
        charset = call.request.headers.suitableCharset(),
        typeInfo = typeInfo<T>(),
        content = ByteReadChannel(data)
    )

    return if (result is T) result
    else throw WebsocketDeserializeException("Can't convert value from json")
}



