package org.jetbrains.ktor.websocket

import org.jetbrains.ktor.cio.*
import java.nio.*
import java.util.concurrent.atomic.*

internal class WebSocketReader(val maxFrameSize: () -> Long, val close: suspend (CloseReason?) -> Unit, val sendClose: suspend (CloseReason) -> Unit, val channel: ReadChannel, val frameHandler: suspend (Frame) -> Unit, val lastReason: () -> CloseReason?) {
    private val state = AtomicReference(State.FRAME)
    private val buffer = ByteBuffer.allocate(8192).apply { flip() }
    private val frameParser = FrameParser()
    private val collector = SimpleFrameCollector()
    private var suspended = false

    suspend fun readLoop() {
        while (!suspended) {
            buffer.compact()
            if (channel.read(buffer) == -1) {
                return close(lastReason())
            }
            buffer.flip()
            parseLoop()
        }
    }

    private suspend fun parseLoop() {
        loop@
        while (buffer.hasRemaining()) {
            when (state.get()!!) {
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

    private suspend fun handleFrameIfProduced() {
        if (!collector.hasRemaining) {
            state.set(State.FRAME)
            frameHandler(Frame.byType(frameParser.fin, frameParser.frameType, collector.take(frameParser.maskKey)))
            frameParser.bodyComplete()
        }
    }

    enum class State {
        FRAME,
        BODY
    }
}