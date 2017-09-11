package io.ktor.testing

import kotlinx.coroutines.experimental.*
import io.ktor.cio.*
import io.ktor.content.*
import io.ktor.host.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.util.*
import java.io.*
import java.time.*
import java.util.concurrent.*
import java.util.concurrent.TimeoutException

class TestApplicationResponse(call: TestApplicationCall) : BaseApplicationResponse(call) {
    private val realContent = lazy { ByteBufferWriteChannel() }

    @Volatile
    private var closed = false
    private val webSocketCompleted = CountDownLatch(1)

    override fun setStatus(statusCode: HttpStatusCode) {}

    override val headers: ResponseHeaders = object : ResponseHeaders() {
        private val headersMap = ValuesMapBuilder(true)
        private val headers: ValuesMap by lazy { headersMap.build() }

        override fun hostAppendHeader(name: String, value: String) {
            if (closed)
                throw UnsupportedOperationException("Headers can no longer be set because response was already completed")
            headersMap.append(name, value)
        }

        override fun getHostHeaderNames(): List<String> = headers.names().toList()
        override fun getHostHeaderValues(name: String): List<String> = headers.getAll(name).orEmpty()
    }

    init {
        pipeline.intercept(ApplicationSendPipeline.Host) {
            call.requestHandled = true
            close()
        }
    }

    suspend override fun respondUpgrade(upgrade: FinalContent.ProtocolUpgrade) {
        upgrade.upgrade(call.receiveChannel(), realContent.value, Closeable { webSocketCompleted.countDown() }, CommonPool, Unconfined)
    }

    override suspend fun responseChannel(): WriteChannel = realContent.value.apply {
        headers[HttpHeaders.ContentLength]?.let { contentLengthString ->
            val contentLength = contentLengthString.toLong()
            if (contentLength >= Int.MAX_VALUE) {
                throw IllegalStateException("Content length is too big for test host")
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
        if (!webSocketCompleted.await(duration.toMillis(), TimeUnit.MILLISECONDS))
            throw TimeoutException()
    }
}

fun TestApplicationResponse.contentType(): ContentType {
    val contentTypeHeader = requireNotNull(headers[HttpHeaders.ContentType])
    return ContentType.parse(contentTypeHeader)
}