package org.jetbrains.ktor.websocket

import kotlinx.coroutines.experimental.*
import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.cio.*
import java.io.*
import java.time.*
import java.util.concurrent.*

private val executor by lazy { ScheduledThreadPoolExecutor(Runtime.getRuntime().availableProcessors() * 8) }
private val dispatcher by lazy { executor.asCoroutineDispatcher() }

internal class WebSocketImpl(call: ApplicationCall,
                             val readChannel: ReadChannel,
                             val writeChannel: WriteChannel,
                             val channel: Closeable) : WebSocket(call) {

    private val controlFrameHandler = ControlFrameHandler(this, executor)
    private val outbound = WebSocketWriter(writeChannel)
    private val reader = WebSocketReader(readChannel, { maxFrameSize }, { frameHandler(it) })

    override var masking: Boolean
        get() = outbound.masking
        set(value) {
            outbound.masking = value
        }

    init {
        masking = false
    }

    fun start() {
        launch(dispatcher) {
            try {
                reader.readLoop()
            } catch (tooBig: WebSocketReader.FrameTooBigException) {
                errorHandler(tooBig)
                close(CloseReason(CloseReason.Codes.TOO_BIG, tooBig.message))
            } catch (t: Throwable) {
                errorHandler(t)
                close(CloseReason(CloseReason.Codes.UNEXPECTED_CONDITION, t.javaClass.name))
            } finally {
                terminateConnection(controlFrameHandler.currentReason)
            }
        }
    }

    override var pingInterval: Duration? = null
        set(value) {
            field = value
            if (value == null) {
                controlFrameHandler.cancelPingPong()
            } else {
                controlFrameHandler.schedulePingPong(value)
            }
        }

    override suspend fun frameHandler(frame: Frame) {
        if (frame.frameType.controlFrame) {
            controlFrameHandler.received(frame)
        }

        super.frameHandler(frame)
    }

    override fun enqueue(frame: Frame) {
        if (frame.frameType.controlFrame) {
            throw IllegalArgumentException("You should never enqueue control frames as they are delivery-time sensitive, use send() instead")
        }

        outbound.enqueue(frame)
    }

    suspend override fun flush() {
        try {
            outbound.flush()
        } catch (t: Throwable) {
            try {
                errorHandler(t)
            } finally {
                terminateConnection(controlFrameHandler.currentReason)
                throw t
            }
        }
    }

    override suspend fun send(frame: Frame) {
        if (frame.frameType.controlFrame) {
            controlFrameHandler.sent(frame)
        }

        try {
            outbound.send(frame)
        } catch (t: Throwable) {
            try {
                errorHandler(t)
            } finally {
                terminateConnection(controlFrameHandler.currentReason)
                throw t
            }
        }

        if (frame is Frame.Close) {
            controlFrameHandler.closeSentAndWritten()
        }
    }

    override fun close() {
        controlFrameHandler.stop()

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

        runBlocking {
            closeHandler(null)
        }
    }

    suspend fun terminateConnection(reason: CloseReason?) {
        use {
            controlFrameHandler.stop()
            closeHandler(reason)
        }
    }
}