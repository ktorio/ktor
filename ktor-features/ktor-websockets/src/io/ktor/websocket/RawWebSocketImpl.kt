package io.ktor.websocket

import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.channels.*
import io.ktor.application.*
import io.ktor.cio.*
import java.io.*
import kotlin.coroutines.experimental.*

internal class RawWebSocketImpl(override val call: ApplicationCall,
                                val readChannel: ReadChannel,
                                val writeChannel: WriteChannel,
                                val channel: Closeable,
                                pool: ByteBufferPool = NoPool,
                                val engineContext: CoroutineContext,
                                val userContext: CoroutineContext
) : WebSocketSession {
    private val writer = WebSocketWriter(writeChannel, engineContext, pool)
    private val reader = WebSocketReader(readChannel, this::maxFrameSize, engineContext, pool)

    override val application: Application get() = call.application
    override val incoming: ReceiveChannel<Frame> get() = reader.incoming

    override val outgoing: SendChannel<Frame> get() = writer.outgoing

    override var maxFrameSize: Long = Int.MAX_VALUE.toLong()

    override var masking: Boolean
        get() = writer.masking
        set(value) {
            writer.masking = value
        }

    fun start(handler: suspend WebSocketSession.(WebSocketUpgrade.Dispatchers) -> Unit) {
        val readerJob = reader.start()
        val writerJob = writer.start()

        readerJob.invokeOnCompletion {
            if (it != null) {
                writerJob.cancel(it)
            }
        }

        writerJob.invokeOnCompletion {
            if (it != null) {
                readerJob.cancel(it)
            }
        }

        launch(engineContext) {
            var t: Throwable? = null
            try {
                handler(WebSocketUpgrade.Dispatchers(engineContext, userContext))
            } catch (failed: Throwable) {
                t = failed
            } finally {
                reader.cancel(t)
                outgoing.close(t)
                writer.close()

                try {
                    writer.flush()
                } catch (ignore: Throwable) { // always ignore it as it is already handled
                }

                readerJob.join()
                writerJob.join()

                terminate()
            }
        }
    }

    suspend override fun flush() {
        writer.flush()
    }

    override fun terminate() {
        writer.close()
        reader.cancel()
        outgoing.close()

        try {
            readChannel.close()
        } catch (t: Throwable) {
            application.log.debug("Failed to close read channel")
        }

        try {
            writeChannel.close()
        } catch (t: Throwable) {
            application.log.debug("Failed to close write channel")
        }

        try {
            channel.close()
        } catch (t: Throwable) {
            application.log.debug("Failed to close write channel")
        }
    }
}