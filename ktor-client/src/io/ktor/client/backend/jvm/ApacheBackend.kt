package io.ktor.client.backend.jvm

import io.ktor.cio.ByteBufferWriteChannel
import io.ktor.cio.toInputStream
import io.ktor.cio.toReadChannel
import io.ktor.client.backend.HttpClientBackend
import io.ktor.client.backend.HttpClientBackendFactory
import io.ktor.client.request.HttpRequest
import io.ktor.client.response.HttpResponseBuilder
import io.ktor.client.utils.EmptyBody
import io.ktor.client.utils.HttpProtocolVersion
import io.ktor.client.utils.ReadChannelBody
import io.ktor.client.utils.WriteChannelBody
import io.ktor.http.HttpStatusCode
import io.ktor.util.flattenEntries
import org.apache.http.HttpResponse
import org.apache.http.client.config.CookieSpecs
import org.apache.http.client.config.RequestConfig
import org.apache.http.client.methods.RequestBuilder
import org.apache.http.client.utils.URIBuilder
import org.apache.http.concurrent.FutureCallback
import org.apache.http.entity.ByteArrayEntity
import org.apache.http.entity.InputStreamEntity
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient
import org.apache.http.impl.nio.client.HttpAsyncClients
import java.lang.Exception
import java.util.*
import kotlin.coroutines.experimental.suspendCoroutine


class ApacheBackend : HttpClientBackend {
    private val backend: CloseableHttpAsyncClient

    init {
        val config = RequestConfig.custom().setCookieSpec(CookieSpecs.IGNORE_COOKIES).build()
        backend = HttpAsyncClients.custom().setDefaultRequestConfig(config).build()
        backend.start()
    }

    suspend override fun makeRequest(data: HttpRequest): HttpResponseBuilder {
        val apacheBuilder = RequestBuilder.create(data.method.value)
        with(data) {
            apacheBuilder.uri = URIBuilder().apply {
                scheme = url.scheme
                host = url.host
                port = url.port
                path = url.path
                url.queryParameters.flattenEntries().forEach { (key, value) -> addParameter(key, value) }
            }.build()
        }

        data.headers.entries().forEach { (name, values) ->
            values.forEach { value -> apacheBuilder.addHeader(name, value) }
        }

        val requestPayload = data.payload
        when (requestPayload) {
            is ReadChannelBody -> InputStreamEntity(requestPayload.channel.toInputStream())
            is WriteChannelBody -> {
                val channel = ByteBufferWriteChannel()
                requestPayload.block(channel)
                ByteArrayEntity(channel.toByteArray())
            }
            else -> null
        }?.let { apacheBuilder.entity = it }

        val apacheRequest = apacheBuilder.build()

        val startTime = Date()

        // TODO: suspendCancelableCoroutine fix
        val response = suspendCoroutine<HttpResponse> { continuation ->
            backend.execute(apacheRequest, object : FutureCallback<HttpResponse> {
                override fun cancelled() {
                }

                override fun completed(response: HttpResponse?) {
                    continuation.resume(response!!)
                }

                override fun failed(exception: Exception?) {
                    continuation.resumeWithException(exception!!)
                }
            })
        }
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

            payload = if (entity?.isStreaming == true) ReadChannelBody(entity.content.toReadChannel()) else EmptyBody

        }

//        response.close()
        return builder
    }

    override fun close() {
        backend.close()
    }

    companion object : HttpClientBackendFactory {
        override operator fun invoke(): HttpClientBackend = ApacheBackend()
    }
}

