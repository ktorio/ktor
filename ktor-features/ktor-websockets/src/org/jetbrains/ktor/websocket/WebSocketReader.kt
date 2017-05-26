package org.jetbrains.ktor.websocket

import kotlinx.coroutines.experimental.channels.*
import org.jetbrains.ktor.cio.*
import java.nio.*
import java.nio.channels.*
import kotlin.coroutines.experimental.*

internal class WebSocketReader(val byteChannel: ReadChannel, val maxFrameSize: () -> Long, val ctx: CoroutineContext, pool: ByteBufferPool) {
    private var state = State.HEADER
    private val frameParser = FrameParser()
    private val collector = SimpleFrameCollector()

    private val queue by lazy {
        produce(ctx, capacity = 8) { // lazy - workaround for missing produce(start = false)
            val ticket = pool.allocate(DEFAULT_BUFFER_SIZE)
            try {
                readLoop(ticket.buffer)
            } catch (expected: ClosedChannelException) {
            } finally {
                pool.release(ticket)
            }
        }
    }

    val incoming: ReceiveChannel<Frame> get() = queue

    fun start(): ProducerJob<Frame> {
        return queue.apply { start() }
    }

    fun cancel(t: Throwable? = null) {
        queue.cancel(t)
    }

    private suspend fun ProducerScope<Frame>.readLoop(buffer: ByteBuffer) {
        buffer.clear()

        while (isActive) {
            if (byteChannel.read(buffer) == -1) {
                state = State.END
                break
            }

            buffer.flip()
            parseLoop(buffer)
            buffer.compact()
        }
    }

    private suspend fun ProducerScope<Frame>.parseLoop(buffer: ByteBuffer) {
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

    private suspend fun ProducerScope<Frame>.handleFrameIfProduced() {
        if (!collector.hasRemaining) {
            state = State.HEADER
            send(Frame.byType(frameParser.fin, frameParser.frameType, collector.take(frameParser.maskKey)))
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