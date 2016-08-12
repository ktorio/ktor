package org.jetbrains.ktor.websocket

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.nio.*
import org.jetbrains.ktor.pipeline.*
import java.time.*

internal class WebSocketImpl(call: ApplicationCall, context: PipelineContext<*>, val readChannel: ReadChannel, val writeChannel: WriteChannel) : WebSocket(call, context) {
    private val controlFrameHandler = ControlFrameHandler(this, call.application.executor)
    private val outbound = WebSocketWriter(this, writeChannel, controlFrameHandler)
    private val reader = WebSocketReader(
            { maxFrameSize },
            { closeAsync(it) },
            { send(Frame.Close(it)) },
            readChannel,
            { frameHandler(it) },
            { controlFrameHandler.currentReason })

    fun init() {
        reader.start()
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

    override fun frameHandler(frame: Frame) {
        if (frame.frameType.controlFrame) {
            controlFrameHandler.received(frame)
        }

        super.frameHandler(frame)
    }

    override fun send(frame: Frame) {
        if (frame.frameType.controlFrame) {
            controlFrameHandler.send(frame)
        }

        outbound.send(frame)
    }

    override fun close(): Nothing {
        controlFrameHandler.stop()

        readChannel.close()
        writeChannel.close()

        super.close()
    }

    fun closeAsync(reason: CloseReason?) {
        try {
            controlFrameHandler.stop()

            closeHandler(reason)
        } finally {
            context.runBlockWithResult {
                close()
            }
        }
    }
}