package io.ktor.client.backend.apache

import io.ktor.client.backend.*
import io.ktor.client.request.HttpRequest
import io.ktor.client.response.*
import io.ktor.client.utils.*
import io.ktor.http.*
import io.ktor.http.HttpHeaders
import io.ktor.util.*
import org.apache.http.HttpResponse
import org.apache.http.client.config.*
import org.apache.http.client.methods.*
import org.apache.http.client.utils.*
import org.apache.http.concurrent.*
import org.apache.http.entity.*
import org.apache.http.impl.nio.client.*
import java.io.*
import java.lang.*
import java.util.*
import kotlin.coroutines.experimental.*


class ApacheBackend : HttpClientBackend {
    private val DEFAULT_TIMEOUT: Int = 10_000
    private val backend: CloseableHttpAsyncClient = prepareClient().apply { start() }

    suspend override fun makeRequest(request: HttpRequest): HttpResponseBuilder {
        val apacheRequest = convertRequest(request)

        val sendTime = Date()
        val apacheResponse = suspendCoroutine<HttpResponse> { continuation ->
            backend.execute(apacheRequest, object : FutureCallback<HttpResponse> {
                override fun completed(result: HttpResponse) {
                    continuation.resume(result)
                }

                override fun cancelled() {
                }

                override fun failed(exception: Exception) {
                    continuation.resumeWithException(exception)
                }
            })
        }
        val receiveTime = Date()

        return convertResponse(apacheResponse).apply {
            requestTime = sendTime
            responseTime = receiveTime
        }
    }

    override fun close() {
        backend.close()
    }

    companion object : HttpClientBackendFactory {
        override operator fun invoke(): HttpClientBackend = ApacheBackend()
    }

    private fun prepareClient(): CloseableHttpAsyncClient {
        val clientBuilder = HttpAsyncClients.custom()
        with(clientBuilder) {
            disableAuthCaching()
            disableConnectionState()
            disableCookieManagement()
        }

        return clientBuilder.build()!!
    }

    private fun convertRequest(request: HttpRequest): HttpUriRequest {
        val builder = RequestBuilder.create(request.method.value)
        with(request) {
            builder.uri = URIBuilder().apply {
                scheme = url.scheme
                host = url.host
                port = url.port
                path = url.path

                // if we have `?` in tail of url we should initialise query parameters
                if (request.url.queryParameters?.isEmpty() == true) setParameters(listOf())
                url.queryParameters?.flattenEntries()?.forEach { (key, value) -> addParameter(key, value) }
            }.build()
        }

        request.headers.entries().forEach { (name, values) ->
            if (HttpHeaders.ContentLength == name) return@forEach
            values.forEach { value -> builder.addHeader(name, value) }
        }

        val body = request.body
        when (body) {
            is InputStreamBody -> InputStreamEntity(body.stream)
            is OutputStreamBody -> {
                val stream = ByteArrayOutputStream()
                body.block(stream)
                ByteArrayEntity(stream.toByteArray())
            }
            else -> null
        }?.let { builder.entity = it }

        builder.config = RequestConfig.custom()
                .setRedirectsEnabled(request.followRedirects)
                .setSocketTimeout(DEFAULT_TIMEOUT)
                .setConnectTimeout(DEFAULT_TIMEOUT)
                .setConnectionRequestTimeout(DEFAULT_TIMEOUT)
                .build()

        return builder.build()
    }

    private fun convertResponse(response: HttpResponse): HttpResponseBuilder {
        val statusLine = response.statusLine
        val entity = response.entity

        val builder = HttpResponseBuilder()
        builder.apply {
            status = if (statusLine.reasonPhrase != null) {
                HttpStatusCode(statusLine.statusCode, statusLine.reasonPhrase)
            } else {
                HttpStatusCode.fromValue(statusLine.statusCode)
            }

            headers {
                response.allHeaders.forEach { headerLine ->
                    append(headerLine.name, headerLine.value)
                }
            }

            with(statusLine.protocolVersion) {
                version = HttpProtocolVersion(protocol, major, minor)
            }

            body = if (entity?.isStreaming == true) {
                val stream = entity.content
                origin = Closeable { stream.close() }
                InputStreamBody(stream)
            } else EmptyBody
        }

        return builder
    }
}
