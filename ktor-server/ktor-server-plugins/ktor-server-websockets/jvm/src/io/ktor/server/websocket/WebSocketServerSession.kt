/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.websocket

import io.ktor.http.*
import io.ktor.http.cio.websocket.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.shared.serialization.*
import io.ktor.util.reflect.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.*

/**
 * Represents a server-side web socket session
 */
public interface WebSocketServerSession : WebSocketSession {
    /**
     * Associated received [call] that originating this session
     */
    public val call: ApplicationCall
}

/**
 * Represents a server-side web socket session with all default implementations
 *
 * @see DefaultWebSocketSession
 */
public interface DefaultWebSocketServerSession : DefaultWebSocketSession, WebSocketServerSession

/**
 * An application that started this web socket session
 */
public val WebSocketServerSession.application: Application get() = call.application

public suspend inline fun <reified T> WebSocketServerSession.send(data: Any) {
    val data = application.plugin(WebSockets).contentConverter?.serialize(
        contentType = ContentType.Any,
        charset = call.request.headers.suitableCharset(),
        typeInfo = typeInfo<T>(),
        value = data
    ) ?: throw Exception("No converter")

    val body = when(data) {
        is OutgoingContent.ByteArrayContent -> data.bytes()
        is OutgoingContent.ReadChannelContent -> data.readFrom().readRemaining().readBytes()
        is OutgoingContent.WriteChannelContent -> null
        else -> null
    }?. let { String(it) }
        ?: throw Exception("Can't convert")

    outgoing.send(Frame.Text(body))
}

public suspend inline fun <reified T> WebSocketServerSession.receive(): Any? {
    val frame = incoming.receive()
    if(frame.frameType.controlFrame)
        throw Exception("Control frame")

    val data = when(frame) {
        is Frame.Text -> frame.data
        else -> null
    } ?: throw Exception("null data frame")

    val  byteReadChannel = ByteReadChannel(data)

    return application.plugin(WebSockets).contentConverter?.deserialize(
        charset = call.request.headers.suitableCharset(),
        typeInfo = typeInfo<T>(),
        content = byteReadChannel
    )
}

internal fun WebSocketSession.toServerSession(call: ApplicationCall): WebSocketServerSession =
    DelegatedWebSocketServerSession(call, this)

internal fun DefaultWebSocketSession.toServerSession(call: ApplicationCall): DefaultWebSocketServerSession =
    DelegatedDefaultWebSocketServerSession(call, this)

private class DelegatedWebSocketServerSession(
    override val call: ApplicationCall,
    val delegate: WebSocketSession
) : WebSocketServerSession, WebSocketSession by delegate

private class DelegatedDefaultWebSocketServerSession(
    override val call: ApplicationCall,
    val delegate: DefaultWebSocketSession
) : DefaultWebSocketServerSession, DefaultWebSocketSession by delegate
