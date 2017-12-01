package io.ktor.server.testing

import io.ktor.cio.*
import io.ktor.content.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.server.engine.*
import io.ktor.util.*
import kotlinx.coroutines.experimental.*
import java.time.*
import java.util.concurrent.*

class TestApplicationResponse(call: TestApplicationCall) : BaseApplicationResponse(call) {
    private val realContent = lazy { ByteBufferWriteChannel() }

    @Volatile
    private var closed = false
    private val webSocketCompleted = CompletableDeferred<Unit>()

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

    override suspend fun responseChannel(): WriteChannel = realContent.value.apply {
        headers[HttpHeaders.ContentLength]?.let { contentLengthString ->
            val contentLength = contentLengthString.toLong()
            if (contentLength >= Int.MAX_VALUE) {
                throw IllegalStateException("Content length is too big for test engine")
            }

            ensureCapacity(contentLength.toInt())
        }
    }

    val content: String?
        get() = if (realContent.isInitialized()) {
            realContent.value.toString(headers[HttpHeaders.ContentType]?.let { ContentType.parse(it).charset() } ?: Charsets.UTF_8)
        } else {
            null
        }

    val byteContent: ByteArray?
        get() = if (realContent.isInitialized()) {
            realContent.value.toByteArray()
        } else {
            null
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

fun TestApplicationResponse.contentType(): ContentType {
    val contentTypeHeader = requireNotNull(headers[HttpHeaders.ContentType])
    return ContentType.parse(contentTypeHeader)
}