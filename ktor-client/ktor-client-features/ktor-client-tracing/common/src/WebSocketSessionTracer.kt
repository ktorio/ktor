/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.features.tracing

import io.ktor.http.cio.websocket.*

internal class WebSocketSessionTracer(
    requestId: String,
    tracer: Tracer,
    private val delegate: DefaultWebSocketSession
) : DefaultWebSocketSession by delegate {

    override val incoming = IncomingChannelTracer(requestId, tracer, delegate.incoming)
    override val outgoing = OutgoingChannelTracer(requestId, tracer, delegate.outgoing)
    override suspend fun send(frame: Frame) {
        outgoing.send(frame)
    }
}
