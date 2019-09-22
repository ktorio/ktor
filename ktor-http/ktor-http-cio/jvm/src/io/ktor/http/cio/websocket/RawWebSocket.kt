/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.http.cio.websocket

import io.ktor.util.*
import io.ktor.util.cio.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import io.ktor.utils.io.*
import io.ktor.utils.io.pool.*
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
    private val socketJob: CompletableJob = Job(coroutineContext[Job])

    override val coroutineContext: CoroutineContext = coroutineContext + socketJob + CoroutineName("raw-ws")
    override val incoming: ReceiveChannel<Frame> get() = reader.incoming
    override val outgoing: SendChannel<Frame> get() = writer.outgoing

    override var maxFrameSize: Long by Delegates.observable(maxFrameSize) { _, _, newValue ->
        reader.maxFrameSize = newValue
    }

    override var masking: Boolean by Delegates.observable(masking) { _, _, newValue ->
        writer.masking = newValue
    }

    internal val writer: WebSocketWriter = WebSocketWriter(output, this.coroutineContext, masking, pool)
    internal val reader: WebSocketReader = WebSocketReader(input, Job() + coroutineContext, maxFrameSize, pool)

    init {
        coroutineContext[Job]?.invokeOnCompletion {
            reader.cancel()
        }
    }

    override suspend fun flush(): Unit = writer.flush()

    override fun terminate() {
        outgoing.close()
        socketJob.complete()
    }

    override suspend fun close(cause: Throwable?) {
        if (cause != null) {
            socketJob.completeExceptionally(cause)
            outgoing.close(cause)
        } else terminate()
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
