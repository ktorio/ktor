package io.ktor.server.testing.client

import io.ktor.client.call.*
import io.ktor.client.engine.*
import io.ktor.client.request.*
import io.ktor.content.*
import io.ktor.http.*
import io.ktor.server.testing.*
import io.ktor.util.*
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.io.*
import java.util.concurrent.*

class TestHttpClientConfig : HttpClientEngineConfig() {
    lateinit var app: TestApplicationEngine
}

class TestHttpClientEngine(private val app: TestApplicationEngine) : HttpClientEngine {

    override fun prepareRequest(builder: HttpRequestBuilder, call: HttpClientCall): HttpRequest =
            TestHttpClientRequest(call, this, builder)

    internal fun runRequest(
            method: HttpMethod, url: String, headers: Headers, content: OutgoingContent
    ): TestApplicationCall {
        return app.handleRequest(method, url) {
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
                body = content.toByteReadChannel()
            }
        }
    }

    override fun close() {
        app.stop(0L, 0L, TimeUnit.MILLISECONDS)
    }

    companion object : HttpClientEngineFactory<TestHttpClientConfig> {
        override fun create(block: TestHttpClientConfig.() -> Unit): HttpClientEngine {
            val config = TestHttpClientConfig().apply(block)
            return TestHttpClientEngine(config.app)
        }
    }

    private fun OutgoingContent.toByteReadChannel(): ByteReadChannel = when (this) {
        is OutgoingContent.NoContent -> EmptyByteReadChannel
        is OutgoingContent.ByteArrayContent -> ByteReadChannel(bytes())
        is OutgoingContent.ReadChannelContent -> readFrom()
        is OutgoingContent.WriteChannelContent -> runBlocking {
            writer(coroutineContext) { writeTo(channel) }.channel
        }
        is OutgoingContent.ProtocolUpgrade -> throw UnsupportedContentTypeException(this)
    }
}


