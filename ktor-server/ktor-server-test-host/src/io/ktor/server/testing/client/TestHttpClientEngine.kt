package io.ktor.server.testing.client

import io.ktor.client.call.*
import io.ktor.client.engine.*
import io.ktor.client.request.*
import io.ktor.http.content.*
import io.ktor.http.*
import io.ktor.network.util.*
import io.ktor.server.testing.*
import io.ktor.util.*
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.io.*
import kotlinx.coroutines.experimental.io.ByteReadChannel.*
import java.util.concurrent.*
import kotlin.coroutines.experimental.*

class TestHttpClientEngine(override val config: TestHttpClientConfig) : HttpClientEngine {
    private val app: TestApplicationEngine = config.app

    override val dispatcher: CoroutineDispatcher = ioCoroutineDispatcher

    override suspend fun execute(call: HttpClientCall, data: HttpRequestData): HttpEngineCall {
        val request = TestHttpClientRequest(call, this, data)
        val responseData = with(request) {
            runRequest(method, url.fullPath, headers, content).response
        }

        val clientResponse = TestHttpClientResponse(
            call, responseData.status()!!, responseData.headers.allValues(), responseData.byteContent!!
        )

        return HttpEngineCall(request, clientResponse)
    }

    private fun runRequest(
        method: HttpMethod, url: String, headers: Headers, content: OutgoingContent
    ): TestApplicationCall = app.handleRequest(method, url) {
        headers.flattenForEach { name, value ->
            if (HttpHeaders.ContentLength == name) return@flattenForEach // set later
            if (HttpHeaders.ContentType == name) return@flattenForEach // set later
            addHeader(name, value)
        }

        content.headers.flattenForEach { name, value ->
            if (HttpHeaders.ContentLength == name) return@flattenForEach // TODO: throw exception for unsafe header?
            if (HttpHeaders.ContentType == name) return@flattenForEach
            addHeader(name, value)
        }

        val contentLength = headers[HttpHeaders.ContentLength] ?: content.contentLength?.toString()
        val contentType = headers[HttpHeaders.ContentType] ?: content.contentType?.toString()

        contentLength?.let { addHeader(HttpHeaders.ContentLength, it) }
        contentType?.let { addHeader(HttpHeaders.ContentType, it) }

        if (content !is OutgoingContent.NoContent) {
            bodyChannel = content.toByteReadChannel()
        }
    }

    override fun close() {
        app.stop(0L, 0L, TimeUnit.MILLISECONDS)
    }

    companion object : HttpClientEngineFactory<TestHttpClientConfig> {
        override fun create(block: TestHttpClientConfig.() -> Unit): HttpClientEngine {
            val config = TestHttpClientConfig().apply(block)
            return TestHttpClientEngine(config)
        }
    }

    private fun OutgoingContent.toByteReadChannel(): ByteReadChannel = when (this) {
        is OutgoingContent.NoContent -> ByteReadChannel.Empty
        is OutgoingContent.ByteArrayContent -> ByteReadChannel(bytes())
        is OutgoingContent.ReadChannelContent -> readFrom()
        is OutgoingContent.WriteChannelContent -> runBlocking {
            writer(coroutineContext) { writeTo(channel) }.channel
        }
        is OutgoingContent.ProtocolUpgrade -> throw UnsupportedContentTypeException(this)
    }
}


