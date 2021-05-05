/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.features.tracing

import io.ktor.http.cio.websocket.*
import kotlinx.coroutines.channels.*

internal class OutgoingChannelTracer(
    private val requestId: String,
    private val tracer: Tracer,
    private val delegate: SendChannel<Frame>
) : SendChannel<Frame> by delegate {

    override fun offer(element: Frame): Boolean {
        val result = delegate.offer(element)
        if (result) {
            tracer.webSocketFrameSent(requestId, element)
        }
        return result
    }

    override suspend fun send(element: Frame) {
        delegate.send(element)
        tracer.webSocketFrameSent(requestId, element)
    }
}
