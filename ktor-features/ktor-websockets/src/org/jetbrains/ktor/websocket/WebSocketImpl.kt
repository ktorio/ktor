package org.jetbrains.ktor.websocket

import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.future.*
import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.cio.*
import org.jetbrains.ktor.pipeline.*
import java.io.*
import java.time.*

internal class WebSocketImpl(call: ApplicationCall,
                             val readChannel: ReadChannel,
                             val writeChannel: WriteChannel,
                             val channel: Closeable) : WebSocket(call) {
    private val controlFrameHandler = ControlFrameHandler(this, application.executor)
    private val outbound = WebSocketWriter(this, writeChannel, controlFrameHandler)
    private val reader = WebSocketReader(
            { maxFrameSize },
            { closeAsync(it) },
            { send(Frame.Close(it)) },
            readChannel,
            { frameHandler(it) },
            { controlFrameHandler.currentReason })

    fun init() {
        future(application.executor.asCoroutineDispatcher()) {
            reader.readLoop()
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
        outbound.flush()
    }

    override suspend fun send(frame: Frame) {
        if (frame.frameType.controlFrame) {
            controlFrameHandler.send(frame)
        }

        outbound.send(frame)
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
    }

    suspend fun closeAsync(reason: CloseReason?) {
        use {
            controlFrameHandler.stop()
            closeHandler(reason)
        }
    }
}