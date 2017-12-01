package io.ktor.server.testing.client

import io.ktor.cio.*
import io.ktor.client.call.*
import io.ktor.client.engine.*
import io.ktor.client.request.*
import io.ktor.content.*
import io.ktor.http.*
import io.ktor.network.util.*
import io.ktor.server.testing.*
import io.ktor.util.*
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.io.*
import java.util.concurrent.*

private val EmptyByteArray = ByteArray(0)

class TestHttpClientConfig : HttpClientEngineConfig() {
    lateinit var app: TestApplicationEngine
}

class TestHttpClientEngine(private val app: TestApplicationEngine) : HttpClientEngine {

    override fun prepareRequest(builder: HttpRequestBuilder, call: HttpClientCall): HttpRequest = TestHttpClientRequest(call, this, builder)

    internal fun runRequest(method: HttpMethod, url: String, headers: Headers, content: OutgoingContent): TestApplicationCall {
        return app.handleRequest(method, url) {
            headers.flattenEntries().forEach { (first, second) ->
                addHeader(first, second)
            }

            content.headers.flattenEntries().forEach { (first, second) ->
                addHeader(first, second)
            }

            if (content !is OutgoingContent.NoContent) {
                bodyBytes = content.toByteArray()
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

    private fun OutgoingContent.toByteArray(): ByteArray = when (this) {
        is OutgoingContent.NoContent -> EmptyByteArray
        is OutgoingContent.ByteArrayContent -> bytes()
        is OutgoingContent.ReadChannelContent -> runBlocking { readFrom().toByteArray() }
        is OutgoingContent.WriteChannelContent -> runBlocking {
            writer(ioCoroutineDispatcher) { writeTo(channel) }.channel.toByteArray()
        }
        is OutgoingContent.ProtocolUpgrade -> throw UnsupportedContentTypeException(this)
    }
}


