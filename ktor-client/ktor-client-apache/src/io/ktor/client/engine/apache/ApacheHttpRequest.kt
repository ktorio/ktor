package io.ktor.client.engine.apache

import io.ktor.cio.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.response.*
import io.ktor.client.utils.*
import io.ktor.content.*
import io.ktor.http.*
import io.ktor.util.*
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.io.*
import org.apache.http.client.config.*
import org.apache.http.client.methods.*
import org.apache.http.client.utils.*
import org.apache.http.entity.*
import org.apache.http.impl.nio.client.*
import java.io.*
import java.util.*
import javax.net.ssl.*


internal data class ApacheEngineResponse(val engineResponse: org.apache.http.HttpResponse, val responseReader: Closeable)

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

    override val executionContext: Job = CompletableDeferred<Unit>()

    suspend override fun execute(content: OutgoingContent): HttpResponse {
        val sendTime = Date()
        val requestBuilder = setupRequest()
        writeBody(requestBuilder, content)

        return engine.sendRequest(call, requestBuilder.build(), dispatcher)
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
            is OutgoingContent.ByteArrayContent -> ByteReadChannel(content.bytes()).toInputStream(executionContext)
            is OutgoingContent.ReadChannelContent -> content.readFrom().toInputStream(executionContext)
            is OutgoingContent.WriteChannelContent -> {
                writer(dispatcher, parent = executionContext) {
                    content.writeTo(channel)
                }.channel.toInputStream(executionContext)
            }
            is OutgoingContent.ProtocolUpgrade -> throw UnsupportedContentTypeException(content)
        }

        val length = content.headers[HttpHeaders.ContentLength]?.toLong() ?: -1L
        builder.entity = InputStreamEntity(bodyStream, length)
    }

}
