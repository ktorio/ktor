package org.jetbrains.ktor.websocket

import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.channels.*
import org.jetbrains.ktor.cio.*
import java.nio.*
import kotlin.coroutines.experimental.*

internal class WebSocketReader(val channel: ReadChannel, val maxFrameSize: () -> Long, val destination: SendChannel<Frame>) {
    private var state = State.HEADER
    private val frameParser = FrameParser()
    private val collector = SimpleFrameCollector()

    fun start(ctx: CoroutineContext, pool: ByteBufferPool): Job {
        return launch(ctx) {
            val ticket = pool.allocate(DEFAULT_BUFFER_SIZE)
            try {
                readLoop(ticket.buffer)
            } finally {
                pool.release(ticket)
            }
        }
    }

    private suspend fun readLoop(buffer: ByteBuffer) {
        buffer.clear()

        while (true) {
            if (channel.read(buffer) == -1) {
                state = State.END
                break
            }

            buffer.flip()
            parseLoop(buffer)
            buffer.compact()
        }
    }

    private suspend fun parseLoop(buffer: ByteBuffer) {
        while (buffer.hasRemaining()) {
            when (state) {
                State.HEADER -> {
                    frameParser.frame(buffer)

                    if (frameParser.bodyReady) {
                        state = State.BODY
                        if (frameParser.length > Int.MAX_VALUE || frameParser.length > maxFrameSize()) {
                            throw FrameTooBigException(frameParser.length)
                        }

                        collector.start(frameParser.length.toInt(), buffer)
                        handleFrameIfProduced()
                    } else {
                        return
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
            state = State.HEADER
            destination.send(Frame.byType(frameParser.fin, frameParser.frameType, collector.take(frameParser.maskKey)))
            frameParser.bodyComplete()
        }
    }

    class FrameTooBigException(val frameSize: Long) : Exception() {
        override val message: String
            get() = "Frame is too big: $frameSize"
    }

    enum class State {
        HEADER,
        BODY,
        END
    }
}