package org.jetbrains.ktor.websocket

import org.jetbrains.ktor.cio.*
import java.nio.*
import java.util.concurrent.atomic.*

internal class WebSocketReader(val channel: ReadChannel, val maxFrameSize: () -> Long, val frameHandler: suspend (Frame) -> Unit) {
    private val state = AtomicReference(State.FRAME)
    private val buffer = ByteBuffer.allocate(8192).apply { flip() }
    private val frameParser = FrameParser()
    private val collector = SimpleFrameCollector()
    private var suspended = false

    suspend fun readLoop() {
        while (!suspended) {
            buffer.compact()
            if (channel.read(buffer) == -1) {
                state.set(State.END)
                break
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
                            suspended = true
                            throw FrameTooBigException(frameParser.length)
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
                State.END -> return
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

    class FrameTooBigException(val frameSize: Long) : Exception() {
        override val message: String
            get() = "Frame is too big: $frameSize"
    }

    enum class State {
        FRAME,
        BODY,
        END
    }
}