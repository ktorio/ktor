package io.ktor.client.engine.jetty

import io.ktor.util.cio.*
import io.ktor.client.call.*
import io.ktor.client.engine.*
import io.ktor.client.request.*
import io.ktor.client.response.*
import io.ktor.client.utils.*
import io.ktor.http.*
import io.ktor.http.HttpMethod
import io.ktor.http.content.*
import io.ktor.util.*
import io.ktor.util.date.*
import kotlinx.coroutines.*
import kotlinx.coroutines.io.*
import org.eclipse.jetty.http.*
import org.eclipse.jetty.http2.client.*
import org.eclipse.jetty.http2.frames.*
import java.io.Closeable
import java.nio.*
import kotlin.coroutines.*


internal class JettyHttpRequest(
    override val call: HttpClientCall,
    private val client: JettyHttp2Engine,
    requestData: HttpRequestData,
    override val coroutineContext: CoroutineContext
) : HttpRequest {
    override val attributes: Attributes = requestData.attributes

    override val method: HttpMethod = requestData.method
    override val url: Url = requestData.url
    override val headers: Headers = requestData.headers

    override val content: OutgoingContent = requestData.body as OutgoingContent

    internal suspend fun execute(): HttpResponse {
        val requestTime = GMTDate()
        val session: HTTP2ClientSession = client.connect(url.host, url.port).apply {
            settings(SettingsFrame(emptyMap(), true), org.eclipse.jetty.util.Callback.NOOP)
        } as HTTP2ClientSession

        val headersFrame = prepareHeadersFrame(content)

        val bodyChannel = ByteChannel()
        val responseListener = JettyResponseListener(session, bodyChannel, coroutineContext)

        val jettyRequest = JettyHttp2Request(withPromise { promise ->
            session.newStream(headersFrame, promise, responseListener)
        })

        sendRequestBody(jettyRequest, content)

        val (status, headers) = responseListener.awaitHeaders()
        val origin = Closeable { bodyChannel.close() }
        return JettyHttpResponse(call, status, headers, requestTime, bodyChannel, origin, coroutineContext)
    }


    private fun prepareHeadersFrame(content: OutgoingContent): HeadersFrame {
        val rawHeaders = HttpFields()

        mergeHeaders(headers, content) { name, value ->
            rawHeaders.add(name, value)
        }

        val meta = MetaData.Request(
            method.value,
            url.protocol.name,
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
                val source = GlobalScope.writer(coroutineContext) { content.writeTo(channel) }.channel
                source.writeResponse(request)
            }
            is OutgoingContent.ProtocolUpgrade -> throw UnsupportedContentTypeException(content)
        }
    }

    private fun ByteReadChannel.writeResponse(request: JettyHttp2Request): Job = launch {
        val buffer = HttpClientDefaultPool.borrow()
        pass(buffer) { request.write(it) }
        HttpClientDefaultPool.recycle(buffer)
        request.endBody()
    }
}


