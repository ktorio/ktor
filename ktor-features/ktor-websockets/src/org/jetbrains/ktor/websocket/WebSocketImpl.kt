package org.jetbrains.ktor.websocket

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.nio.*
import org.jetbrains.ktor.pipeline.*
import org.jetbrains.ktor.util.*
import java.time.*
import java.util.concurrent.*

internal class WebSocketImpl(call: ApplicationCall, context: PipelineContext<*>, val readChannel: AsyncReadChannel, val writeChannel: AsyncWriteChannel) : WebSocket(call, context) {
    private val exec = call.application.attributes.computeIfAbsent(ScheduledExecutorAttribute) {
        call.application.closeHooks.add { stopExec() }

        Executors.newScheduledThreadPool(1) { Thread(it, "websocket-pool") }
    }
    private val controlFrameHandler = ControlFrameHandler(this, exec)
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
        controlFrameHandler.cancelAllTimeouts()
        controlFrameHandler.cancelPingPong()

        readChannel.close()
        writeChannel.close()

        super.close()
    }

    fun closeAsync(reason: CloseReason?) {
        try {
            controlFrameHandler.cancelAllTimeouts()
            controlFrameHandler.cancelPingPong()

            closeHandler(reason)
        } finally {
            context.runBlockWithResult {
                close()
            }
        }
    }

    fun stopExec() {
        exec.shutdown()
    }

    private object ScheduledExecutorAttribute : AttributeKey<ScheduledExecutorService>("websocket-exec")
}