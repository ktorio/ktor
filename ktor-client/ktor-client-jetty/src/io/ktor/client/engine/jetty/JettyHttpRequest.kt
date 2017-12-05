package io.ktor.client.engine.jetty

import io.ktor.cio.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.response.*
import io.ktor.client.utils.*
import io.ktor.content.*
import io.ktor.http.*
import io.ktor.http.HttpMethod
import io.ktor.util.*
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.io.*
import org.eclipse.jetty.http.*
import org.eclipse.jetty.http2.api.*
import org.eclipse.jetty.http2.frames.*
import java.io.*
import java.util.*
import javax.net.ssl.*


class JettyHttpRequest(
        override val call: HttpClientCall,
        private val client: JettyHttp2Engine,
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
        val requestTime = Date()
        val session = client.connect(url.host, url.port).apply {
            this.settings(SettingsFrame(emptyMap(), true), org.eclipse.jetty.util.Callback.NOOP)
        }

        val headersFrame = prepareHeadersFrame(content)

        val bodyChannel = ByteChannel()
        val responseListener = JettyResponseListener(bodyChannel, dispatcher)

        val jettyRequest = withPromise<Stream> { promise ->
            session.newStream(headersFrame, promise, responseListener)
        }.let { JettyHttp2Request(it) }

        sendRequestBody(jettyRequest, content)

        val (status, headers) = responseListener.awaitHeaders()
        val origin = Closeable { bodyChannel.close() }
        return JettyHttpResponse(call, status, headers, requestTime, bodyChannel, origin)
    }


    private fun prepareHeadersFrame(content: OutgoingContent): HeadersFrame {
        val rawHeaders = HttpFields()

        headers.flattenEntries().forEach { (name, value) ->
            rawHeaders.add(name, value)
        }

        content.headers.flattenEntries().forEach { (name, value) ->
            rawHeaders.add(name, value)
        }

        val meta = MetaData.Request(
                method.value,
                url.scheme,
                HostPortHttpField("${url.host}:${url.port}"),
                url.fullPath,
                HttpVersion.HTTP_2,
                rawHeaders,
                Long.MIN_VALUE
        )

        return HeadersFrame(meta, null, content is OutgoingContent.NoContent)
    }

    private suspend fun sendRequestBody(request: JettyHttp2Request, content: OutgoingContent) {
        when (content) {
            is OutgoingContent.NoContent -> return
            is OutgoingContent.ByteArrayContent -> {
                request.write(ByteBuffer.wrap(content.bytes()))
                request.endBody()
            }
            is OutgoingContent.ReadChannelContent -> content.readFrom().writeResponse(request)
            is OutgoingContent.WriteChannelContent -> {
                val source = writer(dispatcher + context) { content.writeTo(channel) }.channel
                source.writeResponse(request)
            }
            is OutgoingContent.ProtocolUpgrade -> throw UnsupportedContentTypeException(content)
        }
    }

    private fun ByteReadChannel.writeResponse(request: JettyHttp2Request) = launch(dispatcher + context) {
        val buffer = HttpClientDefaultPool.borrow()
        pass(buffer) { request.write(it) }
        HttpClientDefaultPool.recycle(buffer)
        request.endBody()
    }
}


