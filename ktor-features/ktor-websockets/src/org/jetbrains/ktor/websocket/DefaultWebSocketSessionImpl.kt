package org.jetbrains.ktor.websocket

import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.channels.*
import kotlinx.coroutines.experimental.channels.Channel
import org.jetbrains.ktor.cio.*
import java.time.*
import java.util.concurrent.atomic.*
import kotlin.coroutines.experimental.*
import kotlin.properties.*

internal class DefaultWebSocketSessionImpl(val raw: WebSocketSession,
                                           val hostContext: CoroutineContext,
                                           val userAppContext: CoroutineContext,
                                           val pool: ByteBufferPool
) : DefaultWebSocketSession, WebSocketSession by raw {

    private val pinger = AtomicReference<ActorJob<Frame.Pong>?>(null)
    private val closeReasonRef = AtomicReference<CloseReason?>()
    private val filtered = Channel<Frame>(8)

    override val incoming: ReceiveChannel<Frame> get() = filtered

    override var timeout: Duration = Duration.ofSeconds(15)
    override val closeReason: CloseReason? get() = closeReasonRef.get()
    override var pingInterval: Duration? by Delegates.observable<Duration?>(null, { _, _, _ ->
        runPinger()
    })

    suspend fun run(handler: suspend DefaultWebSocketSession.() -> Unit) {
        runPinger()
        val ponger = ponger(hostContext, this, NoPool)
        val closeSequence = closeSequence(hostContext, raw, { timeout }, { reason ->
            closeReasonRef.compareAndSet(null, reason)
        })

        closeSequence.invokeOnCompletion {
            raw.terminate()
        }

        launch(hostContext) {
            try {
                raw.incoming.consumeEach { frame ->
                    when (frame) {
                        is Frame.Close -> closeSequence.send(CloseFrameEvent.Received(frame))
                        is Frame.Pong -> pinger.get()?.send(frame)
                        is Frame.Ping -> ponger.send(frame)
                        else -> filtered.send(frame)
                    }
                }
            } catch (ignore: ClosedSendChannelException) {
            } catch (t: Throwable) {
                filtered.close(t)
            } finally {
                filtered.close()
                closeSequence.close()
            }
        }

        launch(userAppContext) {
            val t = try {
                handler()
                null
            } catch (t: Throwable) {
                t
            }

            val reason = when (t) {
                null -> CloseReason(CloseReason.Codes.NORMAL, "OK")
                is ClosedReceiveChannelException, is ClosedSendChannelException -> null
                else -> CloseReason(CloseReason.Codes.UNEXPECTED_CONDITION, t.message ?: t.javaClass.name)
            }

            cancelPinger()

            if (reason != null) {
                closeSequence.send(CloseFrameEvent.ToSend(Frame.Close(reason)))
            } else {
                closeSequence.close()
            }
        }.join()

        closeSequence.join()
    }

    private fun runPinger() {
        val newPinger = pingInterval?.let { interval -> pinger(hostContext, raw, interval, timeout, pool, raw.outgoing) }
        pinger.getAndSet(newPinger)?.cancel()
        newPinger?.start()
    }

    private fun cancelPinger() {
        pinger.getAndSet(null)?.cancel()
    }
}