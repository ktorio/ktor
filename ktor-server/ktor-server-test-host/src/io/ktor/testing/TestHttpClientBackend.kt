package io.ktor.testing

import io.ktor.client.backend.*
import io.ktor.client.request.*
import io.ktor.client.response.*
import io.ktor.client.utils.*
import io.ktor.http.*
import io.ktor.util.*
import java.io.*
import java.util.*
import java.util.concurrent.*

class TestHttpClientBackend(val app: TestApplicationHost) : HttpClientBackend {
    suspend override fun makeRequest(request: HttpRequest): HttpResponseBuilder = HttpResponseBuilder().apply {

        val payload = request.payload
        val charset = request.charset ?: Charsets.UTF_8
        val requestBody = when (payload) {
            is InputStreamBody -> InputStreamReader(payload.stream, charset).readText()
            is OutputStreamBody -> {
                val stream = ByteArrayOutputStream()
                payload.block(stream)
                stream.toString(charset.name())
            }
            else -> null
        }

        val call = app.handleRequest(request.method, request.url.fullPath) {
            request.headers.flattenEntries().forEach { (first, second) ->
                addHeader(first, second)
            }

            requestBody?.let { body = requestBody }
        }

        statusCode = call.response.status() ?: HttpStatusCode.NotFound
        version = HttpProtocolVersion("HTTP", 1, 1)
        reason = ""

        requestTime = Date()
        responseTime = Date()

        headers.appendAll(call.response.headers.allValues())
        this.payload = call.response.byteContent?.let { InputStreamBody(ByteArrayInputStream(it)) } ?: EmptyBody
    }

    override fun close() {
        app.stop(0L, 0L, TimeUnit.MILLISECONDS)
    }
}