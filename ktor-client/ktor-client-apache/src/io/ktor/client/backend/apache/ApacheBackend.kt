package io.ktor.client.backend.apache

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

    suspend override fun makeRequest(request: HttpRequest): HttpResponseBuilder {
        val backend: CloseableHttpClient = prepareClient(request)
        val apacheRequest = convertRequest(request)

        val sendTime = Date()
        val apacheResponse = backend.execute(apacheRequest)
        val receiveTime = Date()

        return convertResponse(apacheResponse).apply {
            requestTime = sendTime
            responseTime = receiveTime

            origin = Closeable {
                apacheResponse.close()
                backend.close()
            }
        }
    }

    override fun close() {}

    companion object : HttpClientBackendFactory {
        override operator fun invoke(): HttpClientBackend = ApacheBackend()
    }

    private fun prepareClient(request: HttpRequest): CloseableHttpClient {
        val clientBuilder = HttpClients.custom()
        with(clientBuilder) {
            disableAuthCaching()
            disableAutomaticRetries()
            disableConnectionState()
            disableContentCompression()
            disableCookieManagement()
        }

        request.sslContext?.let {
            clientBuilder.setSSLContext(it)
        }

        return clientBuilder.build()
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
                .build()

        return builder.build()
    }

    private fun convertResponse(response: CloseableHttpResponse): HttpResponseBuilder {
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

            body = if (entity?.isStreaming == true) InputStreamBody(entity.content) else EmptyBody
        }

        return builder
    }
}
