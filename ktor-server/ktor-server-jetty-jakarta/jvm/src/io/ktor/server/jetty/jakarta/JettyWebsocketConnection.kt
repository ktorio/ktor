/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.jetty.jakarta

import io.ktor.http.content.OutgoingContent
import io.ktor.utils.io.*
import io.ktor.utils.io.pool.*
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import org.eclipse.jetty.io.AbstractConnection
import org.eclipse.jetty.io.EndPoint
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import kotlin.coroutines.CoroutineContext

internal class JettyWebsocketConnection(
    private val endpoint: EndPoint,
    private val bufferPool: ByteBufferPool,
    override val coroutineContext: CoroutineContext,
    executor: Executor
) : AbstractConnection(endpoint, executor), CoroutineScope {
    companion object {
        /**
         * Update the [endpoint] field with the supplied connection, then delegate to the
         * [OutgoingContent.ProtocolUpgrade] instance to handle the websocket logic using our
         * [connection]'s channels.
         */
        suspend fun OutgoingContent.ProtocolUpgrade.upgradeAndAwait(
            connection: JettyWebsocketConnection,
            userContext: CoroutineContext
        ) {
            connection.endpoint.upgrade(connection)

            val result = runCatching {
                val job = upgrade(
                    connection.inputChannel,
                    connection.outputChannel,
                    connection.coroutineContext,
                    userContext,
                )
                job.join()
            }

            connection.flushAndClose(result.exceptionOrNull())
        }
    }

    init {
        // for upgraded connections IDLE timeout should be significantly increased
        endPoint.idleTimeout = TimeUnit.MINUTES.toMillis(60L)
    }

    /**
     * Gate for suspending input until content is available.
     */
    private val onFill = Channel<Boolean>(Channel.RENDEZVOUS)

    private val inputJob: WriterJob =
        writer(Dispatchers.IO + coroutineContext + CoroutineName("websocket-input")) {
            val buffer = bufferPool.borrow()
            try {
                while (true) {
                    fillInterested()
                    onFill.receive()
                    when (endpoint.fill(buffer.flip())) {
                        -1 -> break
                        0 -> continue
                        else -> {
                            channel.writeFully(buffer)
                            buffer.compact()
                        }
                    }
                }
            } finally {
                bufferPool.recycle(buffer)
            }
        }

    private val outputJob: ReaderJob =
        reader(Dispatchers.IO + coroutineContext + CoroutineName("websocket-output")) {
            val buffer = bufferPool.borrow()
            try {
                while (true) {
                    when (channel.readAvailable(buffer)) {
                        -1 -> break
                        0 -> continue
                        else -> {}
                    }
                    suspendCancellableCoroutine<Unit> { continuation ->
                        endpoint.write(continuation.asCallback(), buffer.flip())
                    }
                    buffer.compact()
                }
            } finally {
                bufferPool.recycle(buffer)
            }
        }

    private val inputChannel: ByteReadChannel = inputJob.channel
    private val outputChannel: ByteWriteChannel = outputJob.channel

    override fun onFillable() {
        onFill.trySend(true)
    }

    override fun onFillInterestedFailed(cause: Throwable?) {
        onFill.close(cause)
    }

    override fun onClose(cause: Throwable?) {
        runBlocking {
            flushAndClose(cause)
        }
    }

    fun isClosed() = outputJob.isCancelled || outputJob.isCompleted

    suspend fun flushAndClose(cause: Throwable? = null) {
        inputJob.channel.cancel(cause)
        outputChannel.close(cause)
        inputJob.join()
        outputJob.join()
    }

    override fun close() {
        super.close()
    }
}
