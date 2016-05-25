package org.jetbrains.ktor.websocket

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.nio.*
import org.jetbrains.ktor.pipeline.*
import java.nio.*
import java.util.concurrent.atomic.*

internal class WebSocketImpl(call: ApplicationCall, context: PipelineContext<*>, val readChannel: AsyncReadChannel, val writeChannel: AsyncWriteChannel) : WebSocket(call, context) {
    private val state = AtomicReference(State.CREATED)
    private val buffer = ByteBuffer.allocate(8192).apply { flip() }
    override val outbound: WebSocketOutbound = WebSocketWriter(this, writeChannel)

    private val frameParser = FrameParser()
    private val collector = SimpleFrameCollector()

    private val readHandler = object : AsyncHandler {
        override fun success(count: Int) {
            handleRead()
        }

        override fun successEnd() {
            // TODO end
            close()
        }

        override fun failed(cause: Throwable) {
            // TODO end
            close()
        }
    }

    fun init() {
        if (!state.compareAndSet(State.CREATED, State.FRAME)) {
            throw IllegalStateException("Already initialized")
        }

        read()
    }

    private fun read() {
        buffer.compact()
        readChannel.read(buffer, readHandler)
    }

    private fun handleRead() {
        buffer.flip()

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

    private fun handleFrameIfProduced() {
        if (!collector.hasRemaining) {
            state.set(State.FRAME)
            frameHandler(Frame.byType(frameParser.fin, frameParser.frameType, collector.take(frameParser.maskKey)))
            frameParser.bodyComplete()
        }
    }

    override fun close(): Nothing {
        readChannel.close()
        writeChannel.close()

        super.close()
    }

    enum class State {
        CREATED,
        FRAME,
        BODY
    }
}