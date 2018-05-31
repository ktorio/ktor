package io.ktor.http.cio.websocket

import io.ktor.cio.*
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.channels.*
import kotlinx.coroutines.experimental.io.*
import kotlinx.io.pool.*
import java.nio.ByteBuffer
import java.nio.channels.*
import java.util.concurrent.CancellationException
import kotlin.coroutines.experimental.*

/**
 * Class that continuously reads a [byteChannel] and
 * converts into Websocket [Frame] exposing them in [incoming].
 */
class WebSocketReader @Deprecated("Internal API") constructor(
    private val byteChannel: ByteReadChannel,
    var maxFrameSize: Long,
    parent: Job,
    context: CoroutineContext,
    pool: ObjectPool<ByteBuffer> = KtorDefaultPool
) {
    private var state = State.HEADER
    @Suppress("DEPRECATION")
    private val frameParser = FrameParser()
    @Suppress("DEPRECATION")
    private val collector = SimpleFrameCollector()

    private val queue = Channel<Frame>(8)

    private val readerJob = launch(context, parent = parent, start = CoroutineStart.LAZY) {
        val buffer = pool.borrow()
        try {
            readLoop(buffer)
        } catch (expected: ClosedChannelException) {
        } catch (expected: CancellationException) {
            queue.cancel()
        } catch (cause: Throwable) {
            queue.close(cause)
            throw cause
        } finally {
            pool.recycle(buffer)
            queue.close()
        }
    }

    /**
     * Channel receiving Websocket's [Frame] objects read from [byteChannel].
     */
    val incoming: ReceiveChannel<Frame> get() = queue.also { readerJob.start() }

    private suspend fun readLoop(buffer: ByteBuffer) {
        buffer.clear()

        while (true) {
            if (byteChannel.readAvailable(buffer) == -1) {
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
                        if (frameParser.length > Int.MAX_VALUE || frameParser.length > maxFrameSize) {
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
            queue.send(Frame.byType(frameParser.fin, frameParser.frameType, collector.take(frameParser.maskKey)))
            frameParser.bodyComplete()
        }
    }

    class FrameTooBigException(val frameSize: Long) : Exception() {
        override val message: String
            get() = "Frame is too big: $frameSize"
    }

    private enum class State {
        HEADER,
        BODY,
        END
    }
}