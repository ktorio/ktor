package io.ktor.server.testing

import io.ktor.client.backend.*
import io.ktor.client.request.*
import io.ktor.client.response.*
import io.ktor.client.utils.*
import io.ktor.http.*
import io.ktor.util.*
import java.io.*
import java.util.*
import java.util.concurrent.*

class TestHttpClientBackend(val app: TestApplicationEngine) : HttpClientBackend {
    suspend override fun makeRequest(request: HttpRequest): HttpResponseBuilder = HttpResponseBuilder().apply {
        val requestBody = request.body
        val charset = request.charset ?: Charsets.UTF_8

        val bodyStream = when (requestBody) {
            is InputStreamBody -> InputStreamReader(requestBody.stream, charset).readText()
            is OutputStreamBody -> {
                val stream = ByteArrayOutputStream()
                requestBody.block(stream)
                stream.toString(charset.name())
            }
            else -> null
        }

        val call = app.handleRequest(request.method, request.url.fullPath) {
            request.headers.flattenEntries().forEach { (first, second) ->
                addHeader(first, second)
            }

            bodyStream?.let { body = bodyStream }
        }

        status = call.response.status() ?: HttpStatusCode.NotFound
        version = HttpProtocolVersion.HTTP_1_1

        requestTime = Date()
        responseTime = Date()

        headers.appendAll(call.response.headers.allValues())
        body = call.response.byteContent?.let { InputStreamBody(ByteArrayInputStream(it)) } ?: EmptyBody
    }

    override fun close() {
        app.stop(0L, 0L, TimeUnit.MILLISECONDS)
    }
}