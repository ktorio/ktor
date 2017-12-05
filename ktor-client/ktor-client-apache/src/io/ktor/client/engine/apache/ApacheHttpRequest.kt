package io.ktor.client.engine.apache

import io.ktor.cio.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.request.HttpRequest
import io.ktor.client.response.*
import io.ktor.client.utils.*
import io.ktor.content.*
import io.ktor.http.*
import io.ktor.http.HttpHeaders
import io.ktor.util.*
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.io.*
import org.apache.http.*
import org.apache.http.client.config.*
import org.apache.http.client.methods.*
import org.apache.http.client.utils.*
import org.apache.http.concurrent.*
import org.apache.http.entity.*
import org.apache.http.impl.nio.client.*
import org.apache.http.nio.client.methods.*
import java.io.*
import java.util.*
import java.util.concurrent.atomic.*
import javax.net.ssl.*
import kotlin.coroutines.experimental.*


internal data class ApacheEngineResponse(val engineResponse: HttpResponse, val responseReader: Closeable)

class ApacheHttpRequest(
        override val call: HttpClientCall,
        private val engine: CloseableHttpAsyncClient,
        private val config: ApacheEngineConfig,
        private val dispatcher: CoroutineDispatcher,
        builder: HttpRequestBuilder
) : HttpRequest {
    override val attributes: Attributes = Attributes()

    override val method: HttpMethod = builder.method
    override val url: Url = builder.url.build()
    override val headers: Headers = builder.headers.build()

    override val sslContext: SSLContext? = builder.sslContext

    override val context: Job = Job()

    suspend override fun execute(content: OutgoingContent): BaseHttpResponse {
        val builder = setupRequest()
        writeBody(builder, content)

        val sendTime = Date()
        val responseChannel = ByteChannel()

        return ApacheHttpResponse(call, sendRequest(builder.build(), responseChannel), responseChannel, sendTime)
    }

    private fun setupRequest(): RequestBuilder {
        val builder = RequestBuilder.create(method.value)!!
        builder.uri = URIBuilder().apply {
            scheme = url.scheme
            host = url.host
            port = url.port
            path = url.path

            // if we have `?` in tail of url we should initialise query parameters
            if (url.queryParameters?.isEmpty() == true) setParameters(listOf())
            url.queryParameters?.flattenEntries()?.forEach { (key, value) -> addParameter(key, value) }
        }.build()

        headers.flattenEntries().forEach { (key, value) ->
            if (HttpHeaders.ContentLength == key) return@forEach
            builder.addHeader(key, value)
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


        return builder
    }

    private fun writeBody(builder: RequestBuilder, content: OutgoingContent) {
        content.headers.flattenEntries().forEach { (key, value) ->
            if (HttpHeaders.ContentLength == key) return@forEach
            builder.addHeader(key, value)
        }

        val bodyStream = when (content) {
            is OutgoingContent.NoContent -> return
            is OutgoingContent.ByteArrayContent -> content.bytes().inputStream()
            is OutgoingContent.ReadChannelContent -> content.readFrom().toInputStream()
            is OutgoingContent.WriteChannelContent -> {
                writer(dispatcher + context) {
                    content.writeTo(channel)
                }.channel.toInputStream()
            }
            is OutgoingContent.ProtocolUpgrade -> throw UnsupportedContentTypeException(content)
        }

        val length = content.headers[HttpHeaders.ContentLength]?.toLong() ?: -1L
        builder.entity = InputStreamEntity(bodyStream, length)
    }

    private suspend fun sendRequest(
            apacheRequest: HttpUriRequest,
            responseChannel: ByteWriteChannel
    ): ApacheEngineResponse = suspendCoroutine { continuation ->
        val completed = AtomicBoolean(false)
        val consumer = ApacheResponseConsumer(responseChannel, dispatcher + context) {
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

        engine.execute(HttpAsyncMethods.create(apacheRequest), consumer, callback)
    }
}
