package io.ktor.client.backend.jvm

import io.ktor.client.backend.HttpClientBackend
import io.ktor.client.backend.HttpClientBackendFactory
import io.ktor.client.request.HttpRequest
import io.ktor.client.response.HttpResponseBuilder
import io.ktor.client.utils.EmptyBody
import io.ktor.client.utils.HttpProtocolVersion
import io.ktor.client.utils.InputStreamBody
import io.ktor.client.utils.OutputStreamBody
import io.ktor.http.HttpStatusCode
import io.ktor.util.flattenEntries
import org.apache.http.client.config.CookieSpecs
import org.apache.http.client.config.RequestConfig
import org.apache.http.client.methods.CloseableHttpResponse
import org.apache.http.client.methods.RequestBuilder
import org.apache.http.client.utils.URIBuilder
import org.apache.http.entity.ByteArrayEntity
import org.apache.http.entity.InputStreamEntity
import org.apache.http.impl.client.CloseableHttpClient
import org.apache.http.impl.client.HttpClients
import java.io.ByteArrayOutputStream
import java.util.*


class ApacheBackend : HttpClientBackend {
    private val backend: CloseableHttpClient

    init {
        val config = RequestConfig.custom().setCookieSpec(CookieSpecs.IGNORE_COOKIES).build()
        backend = HttpClients.custom().setDefaultRequestConfig(config).build()
    }

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

