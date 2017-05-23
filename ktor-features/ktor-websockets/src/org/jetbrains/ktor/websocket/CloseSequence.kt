package org.jetbrains.ktor.websocket

import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.channels.*
import java.time.*
import java.util.concurrent.*
import kotlin.coroutines.experimental.*

internal fun closeSequence(ctx: CoroutineContext, w: WebSocketWriter, timeout: Duration, termination: suspend (reason: CloseReason?) -> Unit): ActorJob<CloseFrameEvent> {
    return actor(ctx, start = CoroutineStart.LAZY) {
        val timeoutMillis = timeout.toMillis()
        var reason: CloseReason? = null

        try {
            withTimeoutOrNull(timeoutMillis, TimeUnit.MILLISECONDS) {
                val firstCloseEvent = receive()

                reason = firstCloseEvent.frame.readReason()
                when (firstCloseEvent) {
                    is CloseFrameEvent.ToSend -> {
                        w.send(firstCloseEvent.frame)

                        while (true) {
                            val event = receive()
                            if (event is CloseFrameEvent.Received) break
                        }
                    }

                    is CloseFrameEvent.Received -> {
                        w.send(Frame.Close(reason ?: CloseReason(CloseReason.Codes.NORMAL, "OK")))
                        w.flush()
                    }
                }
            }
        } finally {
            // terminate connection in any case
            termination(reason)
        }
    }
}

sealed class CloseFrameEvent(val frame: Frame.Close) {
    class Received(frame: Frame.Close) : CloseFrameEvent(frame)
    class ToSend(frame: Frame.Close) : CloseFrameEvent(frame)
}