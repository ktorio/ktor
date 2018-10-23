package io.ktor.http.cio.websocket

import io.ktor.util.*
import io.ktor.util.cio.*
import kotlinx.coroutines.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.io.*
import kotlinx.io.pool.*
import java.nio.*
import kotlin.coroutines.*
import kotlin.properties.*

/**
 * Represents a RAW web socket session
 */
@UseExperimental(WebSocketInternalAPI::class)
class RawWebSocket(
    input: ByteReadChannel, output: ByteWriteChannel,
    maxFrameSize: Long = Int.MAX_VALUE.toLong(),
    masking: Boolean = false,
    coroutineContext: CoroutineContext,
    pool: ObjectPool<ByteBuffer> = KtorDefaultPool
) : WebSocketSession {
    private val socketJob = CompletableDeferred<Unit>(coroutineContext[Job])

    override val coroutineContext: CoroutineContext = coroutineContext + socketJob

    override val incoming: ReceiveChannel<Frame> get() = reader.incoming
    override val outgoing: SendChannel<Frame> get() = writer.outgoing

    override var maxFrameSize: Long by Delegates.observable(maxFrameSize) { _, _, newValue ->
        reader.maxFrameSize = newValue
    }

    override var masking: Boolean by Delegates.observable(masking) { _, _, newValue ->
        writer.masking = newValue
    }

    internal val writer = WebSocketWriter(output, this.coroutineContext, masking, pool)
    internal val reader: WebSocketReader = WebSocketReader(input, this.coroutineContext, maxFrameSize, pool)

    override suspend fun flush(): Unit = writer.flush()

    override fun terminate() {
        socketJob.completeExceptionally(CancellationException("WebSockedHandler terminated normally"))
    }

    override suspend fun close(cause: Throwable?) {
        terminate()
    }
}

@Suppress("KDocMissingDocumentation")
@UseExperimental(WebSocketInternalAPI::class)
@InternalAPI
suspend fun RawWebSocket.start(handler: suspend WebSocketSession.() -> Unit) {
    try {
        handler()
        writer.flush()
    } finally {
        terminate()
    }
}
