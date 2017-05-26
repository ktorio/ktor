package org.jetbrains.ktor.websocket

import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.channels.*
import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.cio.*
import java.io.*
import kotlin.coroutines.experimental.*

internal class RawWebSocketImpl(override val call: ApplicationCall,
                                val readChannel: ReadChannel,
                                val writeChannel: WriteChannel,
                                val channel: Closeable,
                                pool: ByteBufferPool = NoPool,
                                val hostContext: CoroutineContext,
                                val userAppContext: CoroutineContext
) : WebSocketSession {
    private val writer = WebSocketWriter(writeChannel, hostContext, pool)
    private val reader = WebSocketReader(readChannel, this::maxFrameSize, hostContext, pool)

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
        reader.start()
        writer.start()

        val handlerJob = launch(hostContext) {
            handler(WebSocketUpgrade.Dispatchers(hostContext, userAppContext))
        }

        handlerJob.invokeOnCompletion { t ->
            reader.cancel(t)
            outgoing.close(t)
            writer.close()

            launch(hostContext) {
                writer.flush()
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