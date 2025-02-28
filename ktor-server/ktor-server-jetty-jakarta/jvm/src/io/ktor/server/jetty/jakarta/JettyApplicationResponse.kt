/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.jetty.jakarta

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.response.*
import io.ktor.utils.io.*
import io.ktor.utils.io.pool.*
import kotlinx.coroutines.*
import kotlinx.io.InternalIoApi
import org.eclipse.jetty.server.Request
import org.eclipse.jetty.server.Response
import org.eclipse.jetty.util.Callback
import java.nio.ByteBuffer
import java.util.concurrent.Executor
import kotlin.coroutines.CoroutineContext

@InternalAPI
public class JettyApplicationResponse(
    call: PipelineCall,
    private val request: Request,
    private val response: Response,
    private val executor: Executor,
    override val coroutineContext: CoroutineContext,
    private val userContext: CoroutineContext,
) : BaseApplicationResponse(call), CoroutineScope {
    private companion object {
        private val bufferPool = ByteBufferPool(bufferSize = 8192)
        private val emptyBuffer = ByteBuffer.allocate(0)
    }
    init {
        pipeline.intercept(ApplicationSendPipeline.Engine) {
            if (responseJob.isInitialized()) {
                responseJob.value.apply {
                    runCatching {
                        flushAndClose()
                    }
                }
            }
        }
    }

    @OptIn(InternalCoroutinesApi::class, InternalIoApi::class)
    private val responseJob: Lazy<ReaderJob> = lazy {
        reader(Dispatchers.IO + coroutineContext + CoroutineName("response-writer")) {
            var count = 0
            val buffer = bufferPool.borrow()
            try {
                while (true) {
                    when (val current = channel.readAvailable(buffer)) {
                        -1 -> break
                        0 -> continue
                        else -> count += current
                    }

                    suspendCancellableCoroutine { continuation ->
                        response.write(
                            channel.isClosedForRead,
                            buffer.flip(),
                            continuation.asCallback()
                        )
                    }
                    buffer.compact()
                }
            } finally {
                bufferPool.recycle(buffer)
                runCatching {
                    if (!response.isCommitted)
                        response.write(true, emptyBuffer, Callback.NOOP)
                }
            }
        }
    }

    override val headers: ResponseHeaders = object : ResponseHeaders() {
        override fun engineAppendHeader(name: String, value: String) {
            response.headers.add(name, value)
        }

        override fun getEngineHeaderNames(): List<String> = response.headers.fieldNamesCollection.toList()
        override fun getEngineHeaderValues(name: String): List<String> = response.headers.getValuesList(name)
    }

    // TODO set idle timeout from websocket config on endpoint
    override suspend fun respondUpgrade(upgrade: OutgoingContent.ProtocolUpgrade) {
        if (responseJob.isInitialized())
            responseJob.value.cancel()

        // use the underlying endpoint instance for two-way connection
        val endpoint = request.connectionMetaData.connection.endPoint
        endpoint.idleTimeout = 6000 * 1000

        val websocketConnection = JettyWebsocketConnection2(
            endpoint,
            executor,
            bufferPool,
            coroutineContext
        )

        suspendCancellableCoroutine { continuation ->
            response.write(true, emptyBuffer, continuation.asCallback())
        }

        endpoint.upgrade(websocketConnection)

        val upgradeJob = upgrade.upgrade(
            websocketConnection.inputChannel,
            websocketConnection.outputChannel,
            coroutineContext,
            userContext,
        )

        upgradeJob.invokeOnCompletion {
            websocketConnection.inputChannel.cancel()
            websocketConnection.outputChannel.close()
        }

        upgradeJob.join()
    }

    override suspend fun respondFromBytes(bytes: ByteArray) {
        response.write(true, ByteBuffer.wrap(bytes), Callback.NOOP)
    }

    override suspend fun respondNoContent(content: OutgoingContent.NoContent) {
        response.write(true, emptyBuffer, Callback.NOOP)
    }

    override suspend fun responseChannel(): ByteWriteChannel =
        responseJob.value.channel

    override fun setStatus(statusCode: HttpStatusCode) {
        response.status = statusCode.value
    }
}
