package io.ktor.client.engine.jetty

import io.ktor.client.engine.*
import io.ktor.client.request.*
import io.ktor.client.response.*
import io.ktor.client.utils.*
import io.ktor.http.*
import io.ktor.network.util.*
import io.ktor.util.*
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.io.*
import org.eclipse.jetty.http.*
import org.eclipse.jetty.http2.api.*
import org.eclipse.jetty.http2.client.*
import org.eclipse.jetty.http2.frames.*
import org.eclipse.jetty.util.ssl.*
import java.io.*
import java.net.*
import java.util.*


class JettyHttp2Engine : HttpClientEngine {
    private val sslContextFactory = SslContextFactory(true)

    private val jettyClient = HTTP2Client().apply {
        addBean(sslContextFactory)
        start()
    }

    suspend override fun makeRequest(request: HttpRequest): HttpResponseBuilder {
        val requestTime = Date()
        val session = connect(request.host, request.port).apply {
            this.settings(SettingsFrame(emptyMap(), true), org.eclipse.jetty.util.Callback.NOOP)
        }

        val headersFrame = prepareHeadersFrame(request)

        val bodyChannel = ByteChannel()
        val responseListener = JettyResponseListener(bodyChannel)

        val jettyRequest = withPromise<Stream> { promise ->
            session.newStream(headersFrame, promise, responseListener)
        }.let { JettyHttp2Request(it) }

        sendRequestBody(jettyRequest, request.body)

        val result = HttpResponseBuilder()
        responseListener.awaitHeaders().let {
            result.status = it.statusCode
            result.headers.appendAll(it.headers)
        }

        with(result) {
            this.requestTime = requestTime
            responseTime = Date()

            version = HttpProtocolVersion.HTTP_2_0
            body = ByteReadChannelBody(bodyChannel)
            origin = Closeable { bodyChannel.close() }
        }

        return result
    }

    override fun close() {
        jettyClient.stop()
    }


    private suspend fun connect(host: String, port: Int): Session {
        return withPromise { promise ->
            jettyClient.connect(sslContextFactory, InetSocketAddress(host, port), Session.Listener.Adapter(), promise)
        }
    }

    private fun prepareHeadersFrame(request: HttpRequest): HeadersFrame {
        val headers = HttpFields()

        request.headers.flattenEntries().forEach { (name, value) ->
            headers.add(name, value)
        }

        val meta = MetaData.Request(
                request.method.value,
                request.url.scheme,
                HostPortHttpField("${request.url.host}:${request.url.port}"),
                request.url.fullPath,
                HttpVersion.HTTP_2,
                headers,
                Long.MIN_VALUE
        )

        return HeadersFrame(meta, null, request.body is EmptyBody)
    }

    private suspend fun sendRequestBody(request: JettyHttp2Request, body: Any) {
        if (body is Unit || body is EmptyBody || body !is HttpMessageBody) return

        val sourceChannel = body.toByteReadChannel()

        launch(ioCoroutineDispatcher) {
            val buffer = HTTP_CLIENT_RESPONSE_POOL.borrow()
            while (!sourceChannel.isClosedForRead) {
                buffer.clear()
                sourceChannel.readAvailable(buffer)

                buffer.flip()
                request.write(buffer)
            }

            request.endBody()
            HTTP_CLIENT_RESPONSE_POOL.recycle(buffer)
        }
    }
}