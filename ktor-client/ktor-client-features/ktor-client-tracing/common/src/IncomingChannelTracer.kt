/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.features.tracing

import io.ktor.http.cio.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*

internal class IncomingChannelTracer(
    private val requestId: String,
    private val tracer: Tracer,
    private val delegate: ReceiveChannel<Frame>
) : ReceiveChannel<Frame> by delegate {

    override fun iterator(): ChannelIterator<Frame> {
        return ChannelIteratorTracer(requestId, tracer, delegate.iterator())
    }

    override fun poll(): Frame? {
        val result = delegate.poll()
        if (result != null) {
            tracer.webSocketFrameReceived(requestId, result)
        }
        return result
    }

    override suspend fun receive(): Frame {
        val result = delegate.receive()
        tracer.webSocketFrameReceived(requestId, result)
        return result
    }

    @OptIn(InternalCoroutinesApi::class)
    override suspend fun receiveOrClosed(): ValueOrClosed<Frame> {
        val result = delegate.receiveOrClosed()
        if (!result.isClosed) {
            tracer.webSocketFrameReceived(requestId, result.value)
        }
        return result
    }

    @OptIn(ObsoleteCoroutinesApi::class)
    override suspend fun receiveOrNull(): Frame? {
        val result = delegate.receiveOrNull()
        if (result != null) {
            tracer.webSocketFrameReceived(requestId, result)
        }
        return result
    }
}

internal class ChannelIteratorTracer(
    private val requestId: String,
    private val tracer: Tracer,
    private val delegate: ChannelIterator<Frame>
) : ChannelIterator<Frame> by delegate {

    override fun next(): Frame {
        val result = delegate.next()
        tracer.webSocketFrameReceived(requestId, result)
        return result
    }
}
