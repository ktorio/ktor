@file:UseExperimental(WebSocketInternalAPI::class)

package io.ktor.http.cio.websocket

import io.ktor.util.cio.*
import kotlinx.coroutines.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.io.*
import kotlinx.io.pool.*
import java.nio.*
import kotlin.coroutines.*
import kotlin.properties.*

@UseExperimental(WebSocketInternalAPI::class)
class RawWebSocket(
    input: ByteReadChannel, output: ByteWriteChannel,
    maxFrameSize: Long = Int.MAX_VALUE.toLong(),
    masking: Boolean = false,
    dispatcher: CoroutineContext,
    pool: ObjectPool<ByteBuffer> = KtorDefaultPool
) : WebSocketSession {
    private val socketJob = Job()

    override val coroutineContext: CoroutineContext = dispatcher + socketJob

    override val incoming: ReceiveChannel<Frame> get() = reader.incoming
    override val outgoing: SendChannel<Frame> get() = writer.outgoing

    override var maxFrameSize: Long by Delegates.observable(maxFrameSize) { _, _, newValue ->
        reader.maxFrameSize = newValue
    }

    override var masking: Boolean by Delegates.observable(masking) { _, _, newValue ->
        writer.masking = newValue
    }

    internal val writer = WebSocketWriter(output, coroutineContext, masking, pool)
    internal val reader: WebSocketReader = WebSocketReader(input, coroutineContext, maxFrameSize, pool)

    override suspend fun flush(): Unit = writer.flush()

    override fun terminate() {
        socketJob.cancel(CancellationException("WebSockedHandler terminated normally"))
    }

    override suspend fun close(cause: Throwable?) {
        terminate()
    }
}

suspend fun RawWebSocket.start(handler: suspend WebSocketSession.() -> Unit) {
    handler()
    writer.flush()
    terminate()
}
