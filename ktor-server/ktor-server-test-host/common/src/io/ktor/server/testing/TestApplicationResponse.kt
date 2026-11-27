/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.testing

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.network.sockets.SocketTimeoutException
import io.ktor.server.engine.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.testing.internal.*
import io.ktor.util.cio.ChannelWriteException
import io.ktor.utils.io.*
import kotlinx.atomicfu.*
import kotlinx.coroutines.*
import kotlin.coroutines.*
import kotlin.time.Duration.Companion.milliseconds

/**
 * A test call response received from a server.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.testing.TestApplicationResponse)
 *
 * @property readResponse if response channel need to be consumed into byteContent
 */
public class TestApplicationResponse(
    call: TestApplicationCall,
    private val readResponse: Boolean = false
) : BaseApplicationResponse(call), CoroutineScope by call {
    private val scope: CoroutineScope get() = this

    private val timeoutAttributes get() = call.attributes.getOrNull(timeoutAttributesKey)

    private val _byteContent = atomic<ByteArray?>(null)

    /**
     * Response body byte content. Could be blocking. Remains `null` until response appears.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.testing.TestApplicationResponse.byteContent)
     */
    public var byteContent: ByteArray?
        get() = _byteContent.value ?: websocketChannel()?.let { channel ->
            maybeRunBlocking { channel.toByteArray() }
        }
        private set(value) {
            _byteContent.value = value
        }

    private var responseJob: Job? = null

    // Streaming responses publish their channel immediately. Buffered responses publish it after request processing.
    internal val responseChannel = CompletableDeferred<ByteReadChannel?>()

    override fun setStatus(statusCode: HttpStatusCode) {}

    override val headers: ResponseHeaders = object : ResponseHeaders() {
        private val builder = HeadersBuilder()

        override fun engineAppendHeader(name: String, value: String) {
            builder.append(name, value)
        }

        override fun getEngineHeaderNames(): List<String> = builder.names().toList()
        override fun getEngineHeaderValues(name: String): List<String> = builder.getAll(name).orEmpty()
    }

    override suspend fun responseChannel(): ByteWriteChannel {
        val result = ByteChannel(autoFlush = true)

        if (readResponse) {
            launchResponseJob(result)
        }

        val job = scope.reader(responseJob ?: EmptyCoroutineContext) {
            val counted = channel.counted()
            val readJob = launch {
                counted.copyAndClose(result)
            }

            configureSocketTimeoutIfNeeded(timeoutAttributes, readJob) { counted.totalBytesRead }
        }

        if (responseJob == null) {
            responseJob = job.job
        }

        if (!readResponse) {
            responseChannel.complete(result)
        }

        return job.channel
    }

    private fun launchResponseJob(source: ByteReadChannel) {
        responseJob = async(Dispatchers.Default) {
            byteContent = source.toByteArray()
        }
    }

    override suspend fun respondWriteChannelContent(content: OutgoingContent.WriteChannelContent) {
        val channel = ByteChannel()
        val output: ByteWriteChannel = channel
        responseChannel.complete(channel)

        try {
            withContext(Dispatchers.IOBridge) {
                val counted = output.counted()
                val timeout = timeoutAttributes?.socketTimeoutMillis
                val timeoutJob = timeout?.let {
                    launch {
                        var previous = counted.totalBytesWritten
                        while (isActive) {
                            delay(timeout.milliseconds)
                            val written = counted.totalBytesWritten
                            if (previous == written) {
                                channel.cancel(SocketTimeoutException("Socket timeout elapsed"))
                                break
                            }
                            previous = written
                        }
                    }
                }

                try {
                    content.writeTo(counted)
                } catch (closed: ClosedWriteChannelException) {
                    throw ChannelWriteException(exception = closed)
                } finally {
                    timeoutJob?.cancel()
                }
            }
        } finally {
            channel.flushAndClose()
        }
    }

    /**
     * Gets a response body content channel.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.testing.TestApplicationResponse.contentChannel)
     */
    public fun contentChannel(): ByteReadChannel? = byteContent?.let { ByteReadChannel(it) }

    internal suspend fun awaitForResponseCompletion() {
        responseJob?.join()
    }

    internal fun completeResponse() {
        responseChannel.complete(_byteContent.value?.let { ByteReadChannel(it) })
    }

    // Websockets & upgrade
    internal val webSocketEstablished: CompletableJob = Job()

    override suspend fun respondUpgrade(upgrade: OutgoingContent.ProtocolUpgrade) {
        upgrade.upgrade(
            call.receiveChannel(),
            responseChannel(),
            call.application.coroutineContext,
            Dispatchers.Default
        )
        webSocketEstablished.complete()
    }

    /**
     * A websocket session's channel.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.testing.TestApplicationResponse.websocketChannel)
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    public fun websocketChannel(): ByteReadChannel? {
        if (readResponse || !responseChannel.isCompleted || responseChannel.isCancelled) return null
        return responseChannel.getCompleted()
    }
}
