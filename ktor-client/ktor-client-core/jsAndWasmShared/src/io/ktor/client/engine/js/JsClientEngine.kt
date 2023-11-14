/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.js

import io.ktor.client.plugins.websocket.*
import kotlinx.coroutines.*
import kotlin.coroutines.*

public expect interface CommonEventListener
public expect class CommonEvent {
    public val type: String
}
public expect abstract class CommonEventTarget
public expect class CommonWebSocket

internal expect fun addEventListener(target: CommonWebSocket, type: String, callback: ((CommonEvent) -> Unit)?)

internal expect fun removeEventListener(target: CommonWebSocket, type: String, callback: ((CommonEvent) -> Unit)?)

internal expect fun close(socket: CommonWebSocket)

internal expect fun eventAsString(event: CommonEvent): String

internal suspend fun CommonWebSocket.awaitConnection(): CommonWebSocket = suspendCancellableCoroutine { continuation ->
    if (continuation.isCancelled) return@suspendCancellableCoroutine

    val eventListener = { it: CommonEvent ->
        when (it.type) {
            "open" -> continuation.resume(this)
            "error" -> {
                continuation.resumeWithException(WebSocketException(eventAsString(it)))
            }
        }
    }

    addEventListener(this@awaitConnection, "open", callback = eventListener)
    addEventListener(this@awaitConnection, "error", callback = eventListener)

    continuation.invokeOnCancellation {
        removeEventListener(this@awaitConnection, "open", callback = eventListener)
        removeEventListener(this@awaitConnection, "error", callback = eventListener)

        if (it != null) {
            close(this@awaitConnection)
        }
    }
}
