package io.ktor.websocket

import io.ktor.application.*
import io.ktor.cio.*
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.channels.*
import kotlinx.coroutines.experimental.io.*
import kotlinx.io.pool.*
import kotlin.coroutines.experimental.*

internal class RawWebSocketImpl(override val call: ApplicationCall,
                                readChannel: ByteReadChannel,
                                writeChannel: ByteWriteChannel,
                                pool: ObjectPool<ByteBuffer> = KtorDefaultPool,
                                val engineContext: CoroutineContext,
                                val userContext: CoroutineContext
) : WebSocketSession {
    private val job = Job()

    private val writer = @Suppress("DEPRECATION") WebSocketWriter(writeChannel, job, engineContext, pool)
    private val reader = @Suppress("DEPRECATION") WebSocketReader(readChannel, this::maxFrameSize, job, engineContext, pool)

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

    override suspend fun flush() = writer.flush()

    override fun terminate() {
        job.cancel(CancellationException("WebSockedHandler terminated normally"))
    }
}