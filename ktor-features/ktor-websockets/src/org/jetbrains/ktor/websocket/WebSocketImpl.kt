package org.jetbrains.ktor.websocket

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.nio.*
import org.jetbrains.ktor.pipeline.*
import org.jetbrains.ktor.util.*
import java.nio.*
import java.time.*
import java.util.concurrent.*
import java.util.concurrent.atomic.*

internal class WebSocketImpl(call: ApplicationCall, context: PipelineContext<*>, val readChannel: AsyncReadChannel, val writeChannel: AsyncWriteChannel) : WebSocket(call, context) {
    private val exec = call.application.attributes.computeIfAbsent(ScheduledExecutorAttribute) { Executors.newScheduledThreadPool(1) }
    private val state = AtomicReference(State.CREATED)
    private val buffer = ByteBuffer.allocate(8192).apply { flip() }
    private val controlFrameHandler = ControlFrameHandler(this, exec)
    private val outbound = WebSocketWriter(this, writeChannel, controlFrameHandler)

    private val frameParser = FrameParser()
    private val collector = SimpleFrameCollector()

    private val readHandler = object : AsyncHandler {
        override fun success(count: Int) {
            handleRead()
        }

        override fun successEnd() {
            closeAsync(controlFrameHandler.currentReason)
        }

        override fun failed(cause: Throwable) {
            closeAsync(null)
            // TODO notify handlers
        }
    }

    fun init() {
        if (!state.compareAndSet(State.CREATED, State.FRAME)) {
            throw IllegalStateException("Already initialized")
        }

        read()
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

    private fun read() {
        buffer.compact()
        readChannel.read(buffer, readHandler)
    }

    private fun handleRead() {
        buffer.flip()

        try {
            parseLoop()
            read()
        } catch (e: Throwable) {
            closeAsync(null)
        }
    }

    private fun parseLoop() {
        loop@
        while (buffer.hasRemaining()) {
            when (state.get()!!) {
                State.CREATED -> throw IllegalStateException("State is CREATED")
                State.FRAME -> {
                    frameParser.frame(buffer)

                    if (frameParser.bodyReady) {
                        state.set(State.BODY)
                        if (frameParser.length > Int.MAX_VALUE) {
                            throw IllegalArgumentException("Frame is too big: ${frameParser.length}")
                        }

                        collector.start(frameParser.length.toInt(), buffer)
                        handleFrameIfProduced()
                    } else {
                        break@loop
                    }
                }
                State.BODY -> {
                    collector.handle(buffer)

                    handleFrameIfProduced()
                }
            }
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

    private fun handleFrameIfProduced() {
        if (!collector.hasRemaining) {
            state.set(State.FRAME)
            frameHandler(Frame.byType(frameParser.fin, frameParser.frameType, collector.take(frameParser.maskKey)))
            frameParser.bodyComplete()
        }
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

    enum class State {
        CREATED,
        FRAME,
        BODY
    }

    private object ScheduledExecutorAttribute : AttributeKey<ScheduledExecutorService>("websocket-exec")
}