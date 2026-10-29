/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.testing.client

import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlin.coroutines.*

internal class TestEngineWebsocketSession(
    callContext: CoroutineContext,
    override val incoming: ReceiveChannel<Frame>,
    private val connection: SendChannel<Frame>
) : WebSocketSession {
    // Our own channel, so a caller may register its own invokeOnClose handler on outgoing.
    private val frames = Channel<Frame>(Channel.UNLIMITED)
    override val outgoing: SendChannel<Frame> get() = frames

    override val coroutineContext: CoroutineContext = callContext + CoroutineName("test-ws")

    override var masking: Boolean
        get() = true
        set(_) {}
    override var maxFrameSize: Long
        get() = Long.MAX_VALUE
        set(_) {}

    override val extensions: List<WebSocketExtension<*>>
        get() = emptyList()

    override suspend fun flush() {}

    suspend fun run() {
        try {
            for (frame in frames) {
                connection.send(frame)
                if (frame is Frame.Close) break
            }
        } catch (cause: Throwable) {
            frames.close(cause)
            throw cause
        } finally {
            frames.close()
        }
    }

    @Deprecated(
        "Use cancel() instead.",
        ReplaceWith("cancel()", "kotlinx.coroutines.cancel"),
        DeprecationLevel.ERROR
    )
    override fun terminate() {
        throw NotImplementedError("error")
    }
}
