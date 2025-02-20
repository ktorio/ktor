/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.testing

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.engine.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.testing.internal.*
import io.ktor.utils.io.*
import kotlinx.atomicfu.*
import kotlinx.coroutines.*
import kotlin.coroutines.*

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
        get() = when {
            _byteContent.value != null -> _byteContent.value
            responseChannel == null -> null
            else -> maybeRunBlocking { responseChannel!!.toByteArray() }
        }
        private set(value) {
            _byteContent.value = value
        }

    private var responseChannel: ByteReadChannel? = null

    private var responseJob: Job? = null

    /**
     * Get completed when a response channel is assigned.
     * A response could have no channel assigned in some particular failure cases so the deferred could
     * remain incomplete or become completed exceptionally.
     */
    internal val responseChannelDeferred: CompletableJob = Job()

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

        responseChannel = result
        responseChannelDeferred.complete()

        return job.channel
    }

    private fun launchResponseJob(source: ByteReadChannel) {
        responseJob = async(Dispatchers.Default) {
            byteContent = source.toByteArray()
        }
    }

    override suspend fun respondOutgoingContent(content: OutgoingContent) {
        super.respondOutgoingContent(content)
        responseChannelDeferred.completeExceptionally(IllegalStateException("No response channel assigned"))
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
    public fun websocketChannel(): ByteReadChannel? = responseChannel
}
