package io.ktor.client.backend.jvm

import io.ktor.client.backend.*
import io.ktor.client.request.*
import io.ktor.client.response.*
import io.ktor.client.utils.*
import io.ktor.http.*
import io.ktor.util.*
import org.apache.http.client.config.*
import org.apache.http.client.methods.*
import org.apache.http.client.utils.*
import org.apache.http.entity.*
import org.apache.http.impl.client.*
import java.io.*
import java.util.*


class ApacheBackend : HttpClientBackend {
    private val backend: CloseableHttpClient = HttpClients.createDefault()

    suspend override fun makeRequest(request: HttpRequest): HttpResponseBuilder {
        val apacheBuilder = RequestBuilder.create(request.method.value)
        with(request) {
            apacheBuilder.uri = URIBuilder().apply {
                scheme = url.scheme
                host = url.host
                port = url.port
                path = url.path
                url.queryParameters.flattenEntries().forEach { (key, value) -> addParameter(key, value) }
            }.build()
        }

        request.headers.entries().forEach { (name, values) ->
            values.forEach { value -> apacheBuilder.addHeader(name, value) }
        }

        val requestPayload = request.payload
        when (requestPayload) {
            is InputStreamBody -> InputStreamEntity(requestPayload.stream)
            is OutputStreamBody -> {
                val stream = ByteArrayOutputStream()
                requestPayload.block(stream)
                ByteArrayEntity(stream.toByteArray())
            }
            else -> null
        }?.let { apacheBuilder.entity = it }

        apacheBuilder.config = RequestConfig.custom()
                .setRedirectsEnabled(request.followRedirects)
                .setCookieSpec(CookieSpecs.IGNORE_COOKIES).build()

        val apacheRequest = apacheBuilder.build()
        val startTime = Date()

        // todo: revert async backend(close problem)
        val response: CloseableHttpResponse = backend.execute(apacheRequest)
        val statusLine = response.statusLine
        val entity = response.entity

        val builder = HttpResponseBuilder()
        builder.apply {
            statusCode = HttpStatusCode.fromValue(statusLine.statusCode)
            reason = statusLine.reasonPhrase
            requestTime = startTime
            responseTime = Date()

            headers {
                response.allHeaders.forEach { headerLine ->
                    append(headerLine.name, headerLine.value)
                }
            }

            with(statusLine.protocolVersion) {
                version = HttpProtocolVersion(protocol, major, minor)
            }

            payload = if (entity?.isStreaming == true) InputStreamBody(entity.content) else EmptyBody
            origin = response
        }

        return builder
    }

    override fun close() {
        backend.close()
    }

    companion object : HttpClientBackendFactory {
        override operator fun invoke(): HttpClientBackend = ApacheBackend()
    }
}

