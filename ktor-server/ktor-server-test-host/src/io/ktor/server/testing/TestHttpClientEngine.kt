package io.ktor.server.testing

import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.client.request.*
import io.ktor.client.response.*
import io.ktor.client.utils.*
import io.ktor.http.*
import io.ktor.util.*
import java.io.*
import java.util.*
import java.util.concurrent.*

class TestHttpClientConfig : HttpClientEngineConfig() {
    lateinit var app: TestApplicationEngine
}

class TestHttpClientEngine(private val app: TestApplicationEngine) : HttpClientEngine {
    suspend override fun makeRequest(request: HttpRequest): HttpResponseBuilder = HttpResponseBuilder().apply {
        val requestBody = request.body
        val charset = request.charset() ?: Charsets.UTF_8

        val content = (requestBody as? HttpMessageBody)?.toByteArray()?.let {
            InputStreamReader(it.inputStream(), charset).readText()
        }

        val call = app.handleRequest(request.method, request.url.fullPath) {
            request.headers.flattenEntries().forEach { (first, second) ->
                addHeader(first, second)
            }

            content?.let { body = content }
        }

        status = call.response.status() ?: HttpStatusCode.NotFound
        version = HttpProtocolVersion.HTTP_1_1

        requestTime = Date()
        responseTime = Date()

        headers.appendAll(call.response.headers.allValues())
        body = call.response.byteContent?.toByteReadChannel()?.let { ByteReadChannelBody(it) } ?: EmptyBody
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
}