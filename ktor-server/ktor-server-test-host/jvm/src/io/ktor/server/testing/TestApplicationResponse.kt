/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.testing

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.server.engine.*
import io.ktor.util.*
import kotlinx.coroutines.*
import io.ktor.utils.io.*
import java.time.*
import kotlin.coroutines.*

/**
 * Represents test call response received from server
 * @property readResponse if response channel need to be consumed into byteContent
 */
class TestApplicationResponse(
    call: TestApplicationCall, readResponse: Boolean = false
) : BaseApplicationResponse(call), CoroutineScope by call {

    /**
     * Response body text content. Could be blocking. Remains `null` until response appears.
     */
    val content: String?
        get() {
            val charset = headers[HttpHeaders.ContentType]?.let { ContentType.parse(it).charset() } ?: Charsets.UTF_8
            return byteContent?.toString(charset)
        }

    @Suppress("CanBePrimaryConstructorProperty")
    @Deprecated("Will be removed from public API", level = DeprecationLevel.ERROR)
    val readResponse: Boolean = readResponse

    /**
     * Response body byte content. Could be blocking. Remains `null` until response appears.
     */
    var byteContent: ByteArray? = null
        get() = when {
            field != null -> field
            responseChannel == null -> null
            else -> runBlocking { responseChannel!!.toByteArray() }
        }
        private set

    @Volatile
    private var responseChannel: ByteReadChannel? = null

    @Volatile
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
            if (call.requestHandled)
                throw UnsupportedOperationException("Headers can no longer be set because response was already completed")
            builder.append(name, value)
        }

        override fun getEngineHeaderNames(): List<String> = builder.names().toList()
        override fun getEngineHeaderValues(name: String): List<String> = builder.getAll(name).orEmpty()
    }

    init {
        pipeline.intercept(ApplicationSendPipeline.Engine) {
            call.requestHandled = true
        }
    }

    override suspend fun responseChannel(): ByteWriteChannel {
        val result = ByteChannel(autoFlush = true)

        if (@Suppress("DEPRECATION_ERROR") readResponse) {
            launchResponseJob(result)
        }

        val job = GlobalScope.reader(responseJob ?: EmptyCoroutineContext) {
            channel.copyAndClose(result, Long.MAX_VALUE)
        }

        if (responseJob == null) {
            responseJob = job
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
     * Response body content channel
     */
    fun contentChannel(): ByteReadChannel? = byteContent?.let { ByteReadChannel(it) }

    /**
     * Await for response job completion
     */
    @InternalAPI
    @Suppress("DeprecatedCallableAddReplaceWith")
    @Deprecated("Will be removed", level = DeprecationLevel.ERROR)
    suspend fun flush() {
        awaitForResponseCompletion()
    }

    internal suspend fun awaitForResponseCompletion() {
        responseJob?.join()
    }

    // Websockets & upgrade
    private val webSocketCompleted: CompletableJob = Job()

    override suspend fun respondUpgrade(upgrade: OutgoingContent.ProtocolUpgrade) {
        upgrade.upgrade(
            call.receiveChannel(),
            responseChannel(),
            Dispatchers.Default,
            Dispatchers.Default
        ).invokeOnCompletion {
            webSocketCompleted.complete()
        }
    }

    /**
     * Wait for websocket session completion
     */
    fun awaitWebSocket(duration: Duration): Unit = runBlocking {
        withTimeout(duration.toMillis()) {
            responseChannelDeferred.join()
            responseJob?.join()
            webSocketCompleted.join()
        }

        Unit
    }

    /**
     * Websocket session's channel
     */
    fun websocketChannel(): ByteReadChannel? = responseChannel
}
