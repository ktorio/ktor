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
import org.apache.http.conn.ssl.*
import org.apache.http.entity.*
import org.apache.http.impl.client.*
import java.io.*
import java.util.*


class ApacheBackend : HttpClientBackend {

    suspend override fun makeRequest(request: HttpRequest): HttpResponseBuilder {
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

        val backend: CloseableHttpClient = clientBuilder.build()

        val apacheBuilder = RequestBuilder.create(request.method.value)
        with(request) {

            apacheBuilder.uri = URIBuilder().apply {
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
            values.forEach { value -> apacheBuilder.addHeader(name, value) }
        }

        val requestBody = request.body
        when (requestBody) {
            is InputStreamBody -> InputStreamEntity(requestBody.stream)
            is OutputStreamBody -> {
                val stream = ByteArrayOutputStream()
                requestBody.block(stream)
                ByteArrayEntity(stream.toByteArray())
            }
            else -> null
        }?.let { apacheBuilder.entity = it }

        apacheBuilder.config = RequestConfig.custom()
                .setRedirectsEnabled(request.followRedirects)
                .build()

        val apacheRequest = apacheBuilder.build()
        val startTime = Date()

        // todo: revert async backend(close problem)
        val response: CloseableHttpResponse = backend.execute(apacheRequest)
        val statusLine = response.statusLine
        val entity = response.entity

        val builder = HttpResponseBuilder()
        builder.apply {
            status = if (statusLine.reasonPhrase != null) {
                HttpStatusCode(statusLine.statusCode, statusLine.reasonPhrase)
            } else {
                HttpStatusCode.fromValue(statusLine.statusCode)
            }

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

            body = if (entity?.isStreaming == true) InputStreamBody(entity.content) else EmptyBody
            origin = Closeable {
                response.close()
                backend.close()
            }
        }

        return builder
    }

    override fun close() {}

    companion object : HttpClientBackendFactory {
        override operator fun invoke(): HttpClientBackend = ApacheBackend()
    }
}

