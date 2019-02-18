package io.ktor.http.cio.websocket

import io.ktor.util.*
import io.ktor.util.cio.*
import kotlinx.atomicfu.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.io.core.*
import kotlinx.io.pool.*
import java.nio.*

private val IncomingProcessorCoroutineName = CoroutineName("ws-incoming-processor")
private val OutgoingProcessorCoroutineName = CoroutineName("ws-outgoing-processor")

/**
 * Default web socket session implementation that handles ping-pongs, close sequence and frame fragmentation
 */
@WebSocketInternalAPI
class DefaultWebSocketSessionImpl(
    private val raw: WebSocketSession,
    pingInterval: Long = -1L,
    override var timeoutMillis: Long = 15000L,
    private val pool: ObjectPool<ByteBuffer> = KtorDefaultPool
) : DefaultWebSocketSession, WebSocketSession by raw {

    private val pinger = atomic<SendChannel<Frame.Pong>?>(null)
    private val closeReasonRef = CompletableDeferred<CloseReason>()
    private val filtered = Channel<Frame>(8)
    private val outgoingToBeProcessed = Channel<Frame>(8)
    private val closed: AtomicBoolean = atomic(false)

    override val incoming: ReceiveChannel<Frame> get() = filtered
    override val outgoing: SendChannel<Frame> get() = outgoingToBeProcessed

    override val closeReason: Deferred<CloseReason?> = closeReasonRef

    override var pingIntervalMillis: Long = pingInterval
        set (newValue) {
            field = newValue
            runOrCancelPinger()
        }

    init {
        runOrCancelPinger()
        runIncomingProcessor(ponger(outgoing, pool))
        runOutgoingProcessor()
    }

    /**
     * Close session with GOING_AWAY reason
     */
    suspend fun goingAway(message: String = "Server is going down") {
        sendCloseSequence(CloseReason(CloseReason.Codes.GOING_AWAY, message))
    }

    /**
     * Close session with the specified [cause] or with no reason if `null`
     */
    @KtorExperimentalAPI
    override suspend fun close(cause: Throwable?) {
        if (closed.value) return

        val reason = when (cause) {
            null -> CloseReason(CloseReason.Codes.NORMAL, "OK")
            is ClosedReceiveChannelException, is ClosedSendChannelException -> null
            else -> CloseReason(CloseReason.Codes.UNEXPECTED_CONDITION, cause.message ?: cause.javaClass.name)
        }

        reason?.let { send(Frame.Close(it)) }
    }

    @UseExperimental(ExperimentalCoroutinesApi::class, ObsoleteCoroutinesApi::class)
    private fun runIncomingProcessor(ponger: SendChannel<Frame.Ping>): Job = launch(
        IncomingProcessorCoroutineName + Dispatchers.Unconfined
    ) {
        var last: BytePacketBuilder? = null
        try {
            raw.incoming.consumeEach { frame ->
                when (frame) {
                    is Frame.Close -> {
                        sendCloseSequence(frame.readReason())
                        return@launch
                    }
                    is Frame.Pong -> pinger.value?.send(frame)
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
            ponger.close(t)
            filtered.close(t)
        } finally {
            ponger.close()
            last?.release()
            filtered.close()
        }
    }

    @UseExperimental(ExperimentalCoroutinesApi::class, ObsoleteCoroutinesApi::class)
    private fun runOutgoingProcessor(): Job = launch(
        OutgoingProcessorCoroutineName + Dispatchers.Unconfined, start = CoroutineStart.UNDISPATCHED
    ) {
        try {
            outgoingToBeProcessed.consumeEach { frame ->
                when (frame) {
                    is Frame.Close -> {
                        sendCloseSequence(frame.readReason())
                        return@consumeEach
                    }
                    else -> raw.outgoing.send(frame)
                }
            }
        } catch (ignore: ClosedSendChannelException) {
        } catch (ignore: ClosedReceiveChannelException) {
        } catch (ignore: CancellationException) {
        } catch (ignore: ChannelIOException) {
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
            runOrCancelPinger()
            raw.outgoing.send(Frame.Close(reasonToSend))
        } finally {
            closeReasonRef.complete(reasonToSend)
        }
    }

    private fun runOrCancelPinger() {
        val interval = pingIntervalMillis

        val newPinger: SendChannel<Frame.Pong>? = when {
            closed.value -> null
            interval >= 0L -> pinger(raw.outgoing, interval, timeoutMillis, pool)
            else -> null
        }

        // pinger is always lazy so we publish it first and then start it by sending EmptyPong
        // otherwise it may send ping before it get published so corresponding pong will not be dispatched to pinger
        // that will cause it to terminate connection on timeout
        pinger.getAndSet(newPinger)?.close()

        newPinger?.offer(EmptyPong) // it is safe here to send dummy pong because pinger will ignore it

        if (closed.value && newPinger != null) {
            runOrCancelPinger()
        }
    }

    companion object {
        private val EmptyPong = Frame.Pong(ByteArray(0))
    }
}

@InternalAPI
@Suppress("KDocMissingDocumentation")
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
