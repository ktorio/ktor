package io.ktor.client.engine.apache

import io.ktor.client.engine.*
import io.ktor.client.request.HttpRequest
import io.ktor.client.response.*
import io.ktor.client.utils.*
import io.ktor.http.*
import io.ktor.http.HttpHeaders
import io.ktor.util.*
import kotlinx.coroutines.experimental.io.*
import org.apache.http.HttpResponse
import org.apache.http.client.config.*
import org.apache.http.client.methods.*
import org.apache.http.client.utils.*
import org.apache.http.concurrent.*
import org.apache.http.entity.*
import org.apache.http.impl.nio.client.*
import org.apache.http.nio.client.methods.*
import java.io.*
import java.lang.*
import java.util.*
import java.util.concurrent.*
import java.util.concurrent.atomic.*
import kotlin.coroutines.experimental.*


internal data class ApacheResponse(val response: HttpResponse, val responseReader: Closeable)

class ApacheEngine(private val config: ApacheEngineConfig) : HttpClientEngine {
    private val backend: CloseableHttpAsyncClient = prepareClient().apply { start() }

    suspend override fun makeRequest(request: HttpRequest): HttpResponseBuilder {
        val apacheRequest = convertRequest(request)
        val sendTime = Date()
        val responseChannel = ByteChannel()
        val (response, responseReader) = sendRequest(apacheRequest, responseChannel)

        val receiveTime = Date()
        return convertResponse(response).apply {
            requestTime = sendTime
            responseTime = receiveTime
            body = ByteReadChannelBody(responseChannel)
            origin = responseReader
        }
    }

    override fun close() {
        backend.close()
    }

    private fun prepareClient(): CloseableHttpAsyncClient {
        val clientBuilder = HttpAsyncClients.custom()
        with(clientBuilder) {
            disableAuthCaching()
            disableConnectionState()
            disableCookieManagement()
        }

        with(config) {
            clientBuilder.customClient()
        }

        config.sslContext?.let { clientBuilder.setSSLContext(it) }
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

        val body = request.body as HttpMessageBody
        val length = request.contentLength() ?: -1
        val chunked = request.headers[HttpHeaders.TransferEncoding] == "chunked"

        if (body !is EmptyBody) {
            val bodyStream = body.toByteReadChannel().toInputStream()
            builder.entity = InputStreamEntity(bodyStream, length.toLong()).apply { isChunked = chunked }
        }

        with(config) {
            builder.config = RequestConfig.custom()
                    .setRedirectsEnabled(followRedirects)
                    .setSocketTimeout(socketTimeout)
                    .setConnectTimeout(connectTimeout)
                    .setConnectionRequestTimeout(connectionRequestTimeout)
                    .customRequest()
                    .build()
        }


        return builder.build()
    }

    private fun convertResponse(response: HttpResponse): HttpResponseBuilder {
        val statusLine = response.statusLine
        val builder = HttpResponseBuilder()

        builder.apply {
            val reason = statusLine.reasonPhrase
            status =
                    if (reason != null) HttpStatusCode(statusLine.statusCode, reason)
                    else HttpStatusCode.fromValue(statusLine.statusCode)

            headers {
                response.allHeaders.forEach { headerLine ->
                    append(headerLine.name, headerLine.value)
                }
            }

            with(statusLine.protocolVersion) {
                version = HttpProtocolVersion(protocol, major, minor)
            }
        }

        return builder
    }

    private suspend fun sendRequest(apacheRequest: HttpUriRequest, responseChannel: ByteWriteChannel): ApacheResponse =
            suspendCoroutine { continuation ->
                val completed = AtomicBoolean(false)
                val consumer = ApacheResponseConsumer(responseChannel) {
                    if (completed.compareAndSet(false, true)) continuation.resume(it)
                }

                val callback = object : FutureCallback<Unit> {
                    override fun failed(exception: Exception) {
                        consumer.release(exception)
                        if (completed.compareAndSet(false, true)) continuation.resumeWithException(exception)
                    }

                    override fun completed(result: Unit) = consumer.release()

                    override fun cancelled() {
                        val cause = CancellationException("ApacheBackend: request canceled")
                        consumer.release(cause)
                        if (completed.compareAndSet(false, true)) continuation.resumeWithException(cause)
                    }
                }

                backend.execute(HttpAsyncMethods.create(apacheRequest), consumer, callback)
            }
}
