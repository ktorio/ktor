package io.ktor.http.cio.websocket

import io.ktor.cio.*
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.channels.*
import kotlinx.io.core.*
import kotlinx.io.pool.*
import java.nio.*
import java.time.*
import java.util.concurrent.atomic.*
import kotlin.coroutines.experimental.*
import kotlin.properties.*

class DefaultWebSocketSessionImpl(
    private val raw: WebSocketSession,
    parent: Job,
    pingInterval: Duration? = null,
    override var timeout: Duration = Duration.ofSeconds(15),
    private val pool: ObjectPool<ByteBuffer> = KtorDefaultPool
) : DefaultWebSocketSession, WebSocketSession by raw {
    private val pinger = AtomicReference<SendChannel<Frame.Pong>?>(null)
    private val closeReasonRef = CompletableDeferred<CloseReason>()
    private val filtered = Channel<Frame>(8)
    private val outgoingToBeProcessed = Channel<Frame>(8)
    private val closed: AtomicBoolean = AtomicBoolean(false)

    override val incoming: ReceiveChannel<Frame> get() = filtered
    override val outgoing: SendChannel<Frame> get() = outgoingToBeProcessed

    override val dispatcher: CoroutineContext = raw.dispatcher

    override val closeReason: Deferred<CloseReason?> = closeReasonRef

    override var pingInterval: Duration? by Delegates.observable(pingInterval) { _, _, newValue ->
        newValue ?: return@observable
        runPinger()
    }

    init {
        runPinger()

        val ponger = ponger(dispatcher, this, pool)

        runIncomingProcessor(ponger).invokeOnCompletion {
            ponger.close()
        }

        runOutgoingProcessor()

        parent.invokeOnCompletion {
            launch(dispatcher, start = CoroutineStart.UNDISPATCHED) {
                outgoingToBeProcessed.send(
                    Frame.Close(CloseReason(CloseReason.Codes.GOING_AWAY, "Server is going down"))
                )
            }
        }
    }

    override suspend fun close(cause: Throwable?) {
        val reason = when (cause) {
            null -> CloseReason(CloseReason.Codes.NORMAL, "OK")
            is ClosedReceiveChannelException, is ClosedSendChannelException -> null
            else -> CloseReason(CloseReason.Codes.UNEXPECTED_CONDITION, cause.message ?: cause.javaClass.name)
        }

        sendCloseSequence(reason)
    }

    private fun runIncomingProcessor(ponger: SendChannel<Frame.Ping>): Job = launch(Unconfined) {
        var last: BytePacketBuilder? = null
        try {
            raw.incoming.consumeEach { frame ->
                when (frame) {
                    is Frame.Close -> {
                        sendCloseSequence(frame.readReason())
                        return@launch
                    }
                    is Frame.Pong -> pinger.get()?.send(frame)
                    is Frame.Ping -> ponger.send(frame)
                    else -> {
                        if (!frame.fin) {
                            if (last == null) last = BytePacketBuilder()
                            last!!.writeFully(frame.buffer)
                            return@consumeEach
                        }

                        val frameToSend = last?.let { builder ->
                            builder.writeFully(frame.buffer)
                            Frame.byType(true, frame.frameType, builder.build().readByteBuffer())
                        } ?: frame

                        last = null
                        filtered.send(frameToSend)
                    }
                }
            }
        } catch (ignore: ClosedSendChannelException) {
        } catch (t: Throwable) {
            filtered.close(t)
        } finally {
            last?.release()
            filtered.close()
        }
    }

    private fun runOutgoingProcessor(): Job = launch(Unconfined) {
        try {
            outgoingToBeProcessed.consumeEach { frame ->
                when (frame) {
                    is Frame.Close -> {
                        sendCloseSequence(frame.readReason())
                        return@launch
                    }
                    else -> raw.outgoing.send(frame)
                }
            }
        } catch (ignore: ClosedSendChannelException) {
        } catch (ignore: ClosedReceiveChannelException) {
        } catch (ignore: CancellationException) {
        } catch (cause: Throwable) {
            raw.outgoing.close(cause)
        } finally {
            raw.outgoing.close()
        }
    }

    private suspend fun sendCloseSequence(reason: CloseReason?) {
        if (!closed.compareAndSet(false, true)) return

        val reasonToSend = reason ?: CloseReason(CloseReason.Codes.NORMAL, "")
        try {
            cancelPinger()
            send(Frame.Close(reasonToSend))
        } finally {
            closeReasonRef.complete(reasonToSend)
        }
    }

    private fun runPinger() {
        if (closed.get()) {
            cancelPinger()
            return
        }

        val newPinger = pingInterval?.let { interval ->
            pinger(dispatcher, raw, interval, timeout, raw.outgoing, pool)
        }

        pinger.getAndSet(newPinger)?.close()
        newPinger?.offer(EmptyPong)
    }

    private fun cancelPinger() {
        pinger.getAndSet(null)?.close()
    }

    companion object {
        private val EmptyPong = Frame.Pong(ByteBuffer.allocate(0))
    }
}

suspend fun DefaultWebSocketSession.run(handler: suspend DefaultWebSocketSession.() -> Unit) {
    val failure = try {
        val me: DefaultWebSocketSession = this@run
        me.handler()
        null
    } catch (failure: Throwable) {
        failure
    }

    close(failure)
}
