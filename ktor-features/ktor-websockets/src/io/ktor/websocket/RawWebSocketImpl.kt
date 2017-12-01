package io.ktor.websocket

import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.channels.*
import io.ktor.application.*
import io.ktor.cio.*
import kotlin.coroutines.experimental.*
import kotlinx.coroutines.experimental.io.*

internal class RawWebSocketImpl(override val call: ApplicationCall,
                                val readChannel: ByteReadChannel,
                                val writeChannel: ByteWriteChannel,
                                pool: ByteBufferPool = NoPool,
                                val engineContext: CoroutineContext,
                                val userContext: CoroutineContext
) : WebSocketSession {
    private val job = Job()

    private val writer = WebSocketWriter(writeChannel, job, engineContext, pool)
    private val reader = WebSocketReader(readChannel, this::maxFrameSize, job, engineContext, pool)

    override val application: Application get() = call.application

    override val incoming: ReceiveChannel<Frame> get() = reader.incoming
    override val outgoing: SendChannel<Frame> get() = writer.outgoing

    override var maxFrameSize: Long = Int.MAX_VALUE.toLong()

    override var masking: Boolean
        get() = writer.masking
        set(value) {
            writer.masking = value
        }

    fun start(handler: suspend WebSocketSession.(WebSocketUpgrade.Dispatchers) -> Unit): Job {
        return launch(engineContext + job) {
            handler(WebSocketUpgrade.Dispatchers(engineContext, userContext))
            writer.flush()
            terminate()
        }
    }

    suspend override fun flush() = writer.flush()

    override fun terminate() {
        job.cancel(CancellationException("WebSockedHandler terminated normally"))
    }
}