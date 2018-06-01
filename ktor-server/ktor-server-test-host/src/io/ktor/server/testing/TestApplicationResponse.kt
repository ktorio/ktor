package io.ktor.server.testing

import io.ktor.cio.*
import io.ktor.http.content.*
import io.ktor.http.*
import io.ktor.network.util.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.server.engine.*
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.io.*
import java.time.*
import java.util.concurrent.*

class TestApplicationResponse(
        call: TestApplicationCall, val readResponse: Boolean = false
) : BaseApplicationResponse(call) {

    val content: String?
        get() {
            val charset = headers[HttpHeaders.ContentType]?.let { ContentType.parse(it).charset() } ?: Charsets.UTF_8
            return byteContent?.toString(charset)
        }

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
    private var responseJob: Job? = null

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
            responseJob = launch(ioCoroutineDispatcher) {
                byteContent = result.toByteArray()
            }
        }

        responseChannel = result
        return result
    }

    fun contentChannel(): ByteReadChannel? = byteContent?.let { ByteReadChannel(it) }

    suspend fun flush() {
        responseJob?.join()
        responseJob?.getCancellationException()?.cause?.let { throw it }
    }

    // Websockets & upgrade
    private val webSocketCompleted: CompletableDeferred<Unit> = CompletableDeferred()

    override suspend fun respondUpgrade(upgrade: OutgoingContent.ProtocolUpgrade) {
        upgrade.upgrade(call.receiveChannel(), responseChannel(), CommonPool, Unconfined).invokeOnCompletion {
            webSocketCompleted.complete(Unit)
        }
    }

    fun awaitWebSocket(duration: Duration) = runBlocking {
        withTimeout(duration.toMillis(), TimeUnit.MILLISECONDS) {
            webSocketCompleted.join()
        }
    }

    fun websocketChannel(): ByteReadChannel? = responseChannel
}
