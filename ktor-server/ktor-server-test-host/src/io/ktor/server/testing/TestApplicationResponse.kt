package io.ktor.server.testing

import io.ktor.cio.*
import io.ktor.content.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.server.engine.*
import io.ktor.util.*
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.io.*
import java.io.*
import java.time.*
import java.util.concurrent.*

class TestApplicationResponse(call: TestApplicationCall) : BaseApplicationResponse(call) {
    private val realContent = lazy { ByteChannel() }
    private val completed: Job = Job()

    @Volatile
    private var closed = false
    private val webSocketCompleted = CompletableDeferred<Unit>()

    val content: String? by lazy {
        val charset = headers[HttpHeaders.ContentType]?.let { ContentType.parse(it).charset() } ?: Charsets.UTF_8
        byteContent?.toString(charset)
    }

    val byteContent: ByteArray? by lazy {
        if (!realContent.isInitialized()) return@lazy null
        runBlocking { realContent.value.toByteArray() }
    }

    override fun setStatus(statusCode: HttpStatusCode) {}

    override val headers: ResponseHeaders = object : ResponseHeaders() {
        private val headersMap = ValuesMapBuilder(true)
        private val headers: ValuesMap by lazy { headersMap.build() }

        override fun engineAppendHeader(name: String, value: String) {
            if (closed)
                throw UnsupportedOperationException("Headers can no longer be set because response was already completed")
            headersMap.append(name, value)
        }

        override fun getEngineHeaderNames(): List<String> = headers.names().toList()
        override fun getEngineHeaderValues(name: String): List<String> = headers.getAll(name).orEmpty()
    }

    init {
        pipeline.intercept(ApplicationSendPipeline.Engine) {
            call.requestHandled = true
            close()
        }
    }

    suspend override fun respondUpgrade(upgrade: OutgoingContent.ProtocolUpgrade) {
        val job = upgrade.upgrade(call.receiveChannel(), realContent.value, CommonPool, Unconfined)
        val registration = job.attachChild(webSocketCompleted)
        webSocketCompleted.invokeOnCompletion {
            registration.dispose()
        }
    }

    override suspend fun responseChannel(): ByteWriteChannel = realContent.value.apply {
        headers[HttpHeaders.ContentLength]?.let { contentLengthString ->
            val contentLength = contentLengthString.toLong()
            if (contentLength >= Int.MAX_VALUE) {
                throw IllegalStateException("Content length is too big for test engine")
            }
        }
    }

    fun contentChannel(): ByteReadChannel? = if (realContent.isInitialized()) realContent.value else null

    fun complete(exception: Throwable? = null) {
        if (exception != null && realContent.isInitialized()) realContent.value.close(exception)
        completed.cancel(exception)
    }

    fun awaitCompletion() = runBlocking {
        val channel = contentChannel()
        if (channel != null) {
            while (!channel.isClosedForRead) {
                channel.read { it.position(it.limit()) }
            }
        }

        completed.join()
        completed.getCancellationException().cause?.let { throw it }
    }

    fun close() {
        closed = true
    }

    fun awaitWebSocket(duration: Duration) {
        runBlocking {
            withTimeout(duration.toMillis(), TimeUnit.MILLISECONDS) {
                webSocketCompleted.join()
            }
        }
    }
}

fun TestApplicationResponse.readBytes(size: Int): ByteArray = runBlocking {
    val result = ByteArray(size)
    contentChannel()!!.readFully(result)
    result
}

fun TestApplicationResponse.contentType(): ContentType {
    val contentTypeHeader = requireNotNull(headers[HttpHeaders.ContentType])
    return ContentType.parse(contentTypeHeader)
}