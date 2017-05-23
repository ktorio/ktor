package org.jetbrains.ktor.websocket

import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.channels.*
import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.cio.*
import java.io.*
import java.time.*
import java.util.concurrent.*
import java.util.concurrent.atomic.*

internal class WebSocketSessionImpl(call: ApplicationCall,
                                    val readChannel: ReadChannel,
                                    val writeChannel: WriteChannel,
                                    val channel: Closeable,
                                    val pool: ByteBufferPool = NoPool,
                                    val webSockets: WebSockets) : WebSocketSession(call) {

    private val messageHandler = actor<Frame>(webSockets.hostDispatcher, capacity = 8, start = CoroutineStart.LAZY) {
        consumeEach { msg ->
            when (msg) {
                is Frame.Close -> {
                    closeSequence.send(CloseFrameEvent.Received(msg))
                }
                is Frame.Pong -> {
                    try {
                        pingPongJob.get()?.send(msg)
                    } catch (ignore: ClosedSendChannelException) {
                    }
                }
                is Frame.Ping -> {
                    ponger.send(msg)
                }
                else -> {
                }
            }

            run(webSockets.appDispatcher) {
                frameHandler(msg)
            }
        }
    }

    private val writer = WebSocketWriter(writeChannel)
    private val reader = WebSocketReader(readChannel, { maxFrameSize }, messageHandler)

    private val closeSequence = closeSequence(webSockets.hostDispatcher, writer, timeout) { reason ->
        launch(webSockets.hostDispatcher) {
            terminateConnection(reason)
        }
    }

    private val pingPongJob = AtomicReference<ActorJob<Frame.Pong>?>()
    private val ponger = ponger(webSockets.hostDispatcher, this, pool)

    override var masking: Boolean
        get() = writer.masking
        set(value) {
            writer.masking = value
        }

    init {
        masking = false
    }

    fun start() {
        reader.start(CommonPool, pool).apply {
            invokeOnCompletion { t ->
                if (t != null) {
                    launch(CommonPool) {
                        errorHandler(t)

                        when (t) {
                            is WebSocketReader.FrameTooBigException -> {
                                close(CloseReason(CloseReason.Codes.TOO_BIG, t.message))
                            }
                            else -> {
                                close(CloseReason(CloseReason.Codes.UNEXPECTED_CONDITION, t.javaClass.name))
                            }
                        }
                    }
                } else {
                    closeSequence.start()
                }
            }
        }

        writer.start(CommonPool, pool).apply {
            invokeOnCompletion { t ->
                if (t != null) {
                    launch(CommonPool) {
                        errorHandler(t)
                    }
                }
                closeSequence.start()
            }
        }
    }

    override var pingInterval: Duration? = null
        set(value) {
            field = value
            if (value == null) {
                pingPongJob.getAndSet(null)?.cancel()
            } else {
                val j = pinger(webSockets.hostDispatcher, this, value, timeout, pool, closeSequence)
                pingPongJob.getAndSet(j)?.cancel()
                j.start()
            }
        }

    suspend override fun flush() {
        writer.flush()
    }

    override suspend fun send(frame: Frame) {
        if (frame is Frame.Close) {
            closeSequence.send(CloseFrameEvent.ToSend(frame))
        } else {
            writer.send(frame)
        }
    }

    suspend override fun awaitClose() {
        closeSequence.join()
    }

    override fun terminate() {
        super.terminate()

        writer.close()
        messageHandler.close()
        pingPongJob.getAndSet(null)?.cancel()
        ponger.close()
        closeSequence.cancel()

        try {
            readChannel.close()
        } catch (t: Throwable) {
            application.log.debug("Failed to close read channel")
        }

        try {
            writeChannel.close()
        } catch (t: Throwable) {
            application.log.debug("Failed to close write channel")
        }

        try {
            channel.close()
        } catch (t: Throwable) {
            application.log.debug("Failed to close write channel")
        }

        runBlocking(webSockets.appDispatcher) {
            closeHandler(null)
        }
    }

    suspend fun terminateConnection(reason: CloseReason?) {
        try {
            run(webSockets.appDispatcher) {
                closeHandler(reason)
            }
        } finally {
            terminate()
        }
    }
}