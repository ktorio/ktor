package org.jetbrains.ktor.websocket

import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.channels.*
import java.time.*
import java.util.concurrent.*
import kotlin.coroutines.experimental.*

fun closeSequence(ctx: CoroutineContext, w: WebSocketSession, timeout: () -> Duration, populateCloseReason: (reason: CloseReason?) -> Unit): ActorJob<CloseFrameEvent> {
    return actor(ctx, start = CoroutineStart.LAZY) {
        var reason: CloseReason? = null

        try {
            val firstCloseEvent = receiveOrNull() ?: return@actor

            withTimeoutOrNull(timeout().toMillis(), TimeUnit.MILLISECONDS) {
                reason = firstCloseEvent.frame.readReason()
                when (firstCloseEvent) {
                    is CloseFrameEvent.ToSend -> {
                        w.send(firstCloseEvent.frame)

                        while (true) {
                            val event = receiveOrNull() ?: break
                            if (event !is CloseFrameEvent.ToSend) break
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
            populateCloseReason(reason)
        }
    }
}

sealed class CloseFrameEvent(val frame: Frame.Close) {
    class Received(frame: Frame.Close) : CloseFrameEvent(frame)
    class ToSend(frame: Frame.Close) : CloseFrameEvent(frame)
}