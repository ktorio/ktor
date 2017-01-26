package org.jetbrains.ktor.websocket

import org.jetbrains.ktor.cio.*
import java.nio.*
import java.util.concurrent.atomic.*

internal class WebSocketReader(val maxFrameSize: () -> Long, val close: (CloseReason?) -> Unit, val sendClose: (CloseReason) -> Unit, val channel: ReadChannel, val frameHandler: (Frame) -> Unit, val lastReason: () -> CloseReason?) {
    private val state = AtomicReference(State.CREATED)
    private val buffer = ByteBuffer.allocate(8192).apply { flip() }
    private val frameParser = FrameParser()
    private val collector = SimpleFrameCollector()
    private var suspended = false

    suspend fun start() {
        if (!state.compareAndSet(State.CREATED, State.FRAME)) {
            throw IllegalStateException("Already initialized")
        }

        read()
    }

    private suspend fun read() {
        if (!suspended) {
            buffer.compact()
            channel.read(buffer)
            buffer.flip()
            parseLoop()
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
                        if (frameParser.length > Int.MAX_VALUE || frameParser.length > maxFrameSize()) {
                            sendClose(CloseReason(CloseReason.Codes.TOO_BIG, "size is ${frameParser.length}"))
                            suspended = true
                            return
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

    private fun handleFrameIfProduced() {
        if (!collector.hasRemaining) {
            state.set(State.FRAME)
            frameHandler(Frame.byType(frameParser.fin, frameParser.frameType, collector.take(frameParser.maskKey)))
            frameParser.bodyComplete()
        }
    }

    enum class State {
        CREATED,
        FRAME,
        BODY
    }
}