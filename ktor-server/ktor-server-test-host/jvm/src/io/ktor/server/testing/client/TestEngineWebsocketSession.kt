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
    override val outgoing: SendChannel<Frame>
) : WebSocketSession {
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
        suspendCancellableCoroutine { cont ->
            outgoing.invokeOnClose { ex ->
                if (ex == null) {
                    cont.resume(Unit)
                } else {
                    cont.resumeWithException(ex)
                }
            }
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
