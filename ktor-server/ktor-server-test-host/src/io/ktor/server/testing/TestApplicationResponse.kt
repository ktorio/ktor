package io.ktor.server.testing

import io.ktor.http.content.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.server.engine.*
import io.ktor.util.*
import io.ktor.util.cio.*
import kotlinx.coroutines.*
import kotlinx.coroutines.io.*
import java.time.*

/**
 * Represents test call response received from server
 * @property readResponse if response channel need to be consumed into byteContent
 */
class TestApplicationResponse(
    call: TestApplicationCall, val readResponse: Boolean = false
) : BaseApplicationResponse(call), CoroutineScope by call {

    /**
     * Response body text content. Could be blocking.
     */
    val content: String?
        get() {
            val charset = headers[HttpHeaders.ContentType]?.let { ContentType.parse(it).charset() } ?: Charsets.UTF_8
            return byteContent?.toString(charset)
        }

    /**
     * Response body byte content. Could be blocking
     */
    var byteContent: ByteArray? = null
        get() = when {
            field != null -> field
            responseChannel == null -> null
            else -> runBlocking { responseChannel!!.toByteArray() }
        }
        private set

    @Volatile
    private var responseChannel: ByteChannel? = null

    @Volatile
    private var responseJob: Deferred<Unit>? = null

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

        if (readResponse) {
            responseJob = async(Dispatchers.Default) {
                byteContent = result.toByteArray()
            }
        }

        responseChannel = result
        return result
    }

    /**
     * Response body content channel
     */
    fun contentChannel(): ByteReadChannel? = byteContent?.let { ByteReadChannel(it) }

    /**
     * Await for response job completion
     */
    @InternalAPI
    suspend fun flush() {
        responseJob?.await()
    }

    // Websockets & upgrade
    private val webSocketCompleted: CompletableDeferred<Unit> = CompletableDeferred()

    override suspend fun respondUpgrade(upgrade: OutgoingContent.ProtocolUpgrade) {
        upgrade.upgrade(call.receiveChannel(), responseChannel(), Dispatchers.Default, Dispatchers.Unconfined)
            .invokeOnCompletion {
                webSocketCompleted.complete(Unit)
            }
    }

    /**
     * Wait for websocket session completion
     */
    fun awaitWebSocket(duration: Duration) = runBlocking {
        withTimeout(duration.toMillis()) {
            webSocketCompleted.join()
        }
    }

    /**
     * Websocket session's channel
     */
    fun websocketChannel(): ByteReadChannel? = responseChannel
}
