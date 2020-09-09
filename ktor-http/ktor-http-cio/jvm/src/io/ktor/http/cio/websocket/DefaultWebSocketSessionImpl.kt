/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.http.cio.websocket

import io.ktor.util.*
import io.ktor.util.cio.*
import io.ktor.utils.io.core.*
import io.ktor.utils.io.pool.*
import kotlinx.atomicfu.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import java.lang.IllegalStateException
import java.nio.*
import kotlin.coroutines.*

private val IncomingProcessorCoroutineName = CoroutineName("ws-incoming-processor")
private val OutgoingProcessorCoroutineName = CoroutineName("ws-outgoing-processor")

private val NORMAL_CLOSE = CloseReason(CloseReason.Codes.NORMAL, "OK")

/**
 * Default web socket session implementation that handles ping-pongs, close sequence and frame fragmentation
 */
@WebSocketInternalAPI
public class DefaultWebSocketSessionImpl(
    private val raw: WebSocketSession,
    pingInterval: Long = -1L,
    override var timeoutMillis: Long = 15000L,
    private val pool: ObjectPool<ByteBuffer> = KtorDefaultPool
) : DefaultWebSocketSession, WebSocketSession {
    private val pinger = atomic<SendChannel<Frame.Pong>?>(null)
    private val closeReasonRef = CompletableDeferred<CloseReason>()
    private val filtered = Channel<Frame>(8)
    private val outgoingToBeProcessed = Channel<Frame>(8)
    private val closed: AtomicBoolean = atomic(false)
    private val context = Job(raw.coroutineContext[Job])

    override val incoming: ReceiveChannel<Frame> get() = filtered
    override val outgoing: SendChannel<Frame> get() = outgoingToBeProcessed

    override val coroutineContext: CoroutineContext =
        raw.coroutineContext + context + CoroutineName("ws-default")

    override var masking: Boolean
        get() = raw.masking
        set(value) {
            raw.masking = value
        }

    override var maxFrameSize: Long
        get() = raw.maxFrameSize
        set(value) {
            raw.maxFrameSize = value
        }
    override val closeReason: Deferred<CloseReason?> = closeReasonRef

    override var pingIntervalMillis: Long = pingInterval
        set(newValue) {
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
    public suspend fun goingAway(message: String = "Server is going down") {
        sendCloseSequence(CloseReason(CloseReason.Codes.GOING_AWAY, message))
    }

    override suspend fun flush() {
        raw.flush()
    }

    @Deprecated(
        "Use cancel() instead.",
        ReplaceWith("cancel()", "kotlinx.coroutines.cancel")
    )
    override fun terminate() {
        context.cancel()
        raw.cancel()
    }

    @OptIn(
        ExperimentalCoroutinesApi::class, ObsoleteCoroutinesApi::class
    )
    private fun runIncomingProcessor(ponger: SendChannel<Frame.Ping>): Job = launch(
        IncomingProcessorCoroutineName + Dispatchers.Unconfined
    ) {
        var last: BytePacketBuilder? = null
        var closeFramePresented = false
        try {
            raw.incoming.consumeEach { frame ->
                when (frame) {
                    is Frame.Close -> {
                        outgoing.send(Frame.Close(frame.readReason() ?: NORMAL_CLOSE))
                        closeFramePresented = true
                        return@launch
                    }
                    is Frame.Pong -> pinger.value?.send(frame)
                    is Frame.Ping -> ponger.send(frame)
                    else -> {
                        checkMaxFrameSize(last, frame)

                        if (!frame.fin) {
                            if (last == null) {
                                last = BytePacketBuilder()
                            }

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
        } catch (cause: Throwable) {
            ponger.close()
            filtered.close(cause)
        } finally {
            ponger.close()
            last?.release()
            filtered.close()

            if (!closeFramePresented) {
                @Suppress("DEPRECATION")
                close(CloseReason(CloseReason.Codes.CLOSED_ABNORMALLY, "Connection was closed without close frame"))
            }
        }
    }

    @OptIn(
        ExperimentalCoroutinesApi::class, ObsoleteCoroutinesApi::class
    )
    private fun runOutgoingProcessor(): Job = launch(
        OutgoingProcessorCoroutineName + Dispatchers.Unconfined, start = CoroutineStart.UNDISPATCHED
    ) {
        try {
            outgoingProcessorLoop()
        } catch (ignore: ClosedSendChannelException) {
        } catch (ignore: ClosedReceiveChannelException) {
        } catch (ignore: CancellationException) {
        } catch (ignore: ChannelIOException) {
        } catch (cause: Throwable) {
            outgoingToBeProcessed.cancel(CancellationException("Failed to send frame", cause))
            raw.closeExceptionally(cause)
        } finally {
            outgoingToBeProcessed.cancel()
            raw.close()
        }
    }

    private suspend fun outgoingProcessorLoop() {
        for (frame in outgoingToBeProcessed) {
            if (frame is Frame.Close) {
                sendCloseSequence(frame.readReason())
                break
            }

            raw.outgoing.send(frame)
        }
    }

    private suspend fun sendCloseSequence(reason: CloseReason?) {
        if (!tryClose()) return
        context.complete()

        val reasonToSend = reason ?: CloseReason(CloseReason.Codes.NORMAL, "")
        try {
            runOrCancelPinger()
            @Suppress("DEPRECATION")
            if (reasonToSend.code != CloseReason.Codes.CLOSED_ABNORMALLY.code) {
                raw.outgoing.send(Frame.Close(reasonToSend))
            }
        } finally {
            closeReasonRef.complete(reasonToSend)
        }
    }

    private fun tryClose(): Boolean = closed.compareAndSet(false, true)

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

    private suspend fun checkMaxFrameSize(
        packet: BytePacketBuilder?,
        frame: Frame
    ) {
        val size = frame.buffer.remaining() + (packet?.size ?: 0)
        if (size > maxFrameSize) {
            packet?.release()
            close(CloseReason(CloseReason.Codes.TOO_BIG, "Frame is too big: $size. Max size is $maxFrameSize"))
            throw WebSocketReader.FrameTooBigException(size.toLong())
        }
    }

    public companion object {
        private val EmptyPong = Frame.Pong(ByteArray(0))
    }
}

@InternalAPI
@Suppress("KDocMissingDocumentation")
public suspend fun DefaultWebSocketSession.run(handler: suspend DefaultWebSocketSession.() -> Unit) {
    try {
        val me: DefaultWebSocketSession = this@run
        me.handler()
        close()
    } catch (failure: Throwable) {
        closeExceptionally(failure)
    }
}
