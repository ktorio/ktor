package io.ktor.client.backend.jetty

import io.ktor.client.*
import io.ktor.client.backend.*
import io.ktor.client.request.*
import io.ktor.client.response.*
import io.ktor.client.utils.*
import io.ktor.network.util.*
import io.ktor.util.*
import kotlinx.coroutines.experimental.*
import org.eclipse.jetty.http.*
import org.eclipse.jetty.http2.api.*
import org.eclipse.jetty.http2.client.*
import org.eclipse.jetty.http2.frames.*
import org.eclipse.jetty.util.ssl.*
import java.io.Closeable
import java.net.*
import java.util.*


class JettyHttp2Backend : HttpClientBackend {
    private val sslContextFactory = SslContextFactory(true)


    private val jettyClient = HTTP2Client().apply {
        addBean(sslContextFactory)
    }

    init {
        jettyClient.start()
    }

    suspend override fun makeRequest(request: HttpRequest): HttpResponseBuilder {
        val requestTime = Date()
        val session = connect(request.host, request.port).apply {
            this.settings(SettingsFrame(emptyMap(), true), org.eclipse.jetty.util.Callback.NOOP)
        }

        val headersFrame = prepareHeadersFrame(request)

        val response = Http2Response()
        val requestChannel = withPromise<Stream> { promise ->
            session.newStream(headersFrame, promise, response.listener)
        }.let { Http2Request(it) }

        sendRequestBody(requestChannel, request.body)

        val result = HttpResponseBuilder()
        response.awaitHeaders().let {
            result.status = it.statusCode
            result.headers.appendAll(it.headers)
        }

        with(result) {
            this.requestTime = requestTime
            responseTime = Date()

            version = HttpProtocolVersion.HTTP_2_0
            body = ByteReadChannelBody(response.channel())
            origin = Closeable { response.close() }
        }

        return result
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

    private suspend fun sendRequestBody(requestChannel: Http2Request, body: Any) {
        if (body is Unit || body is EmptyBody) return
        if (body !is HttpMessageBody) error("Wrong payload type: $body, expected HttpMessageBody")

        val sourceChannel = body.toByteReadChannel()
        launch(ioCoroutineDispatcher) {
            while (!sourceChannel.isClosedForRead) {
                sourceChannel.read { requestChannel.write(it) }
            }

            requestChannel.endBody()
        }
    }

    override fun close() {
        jettyClient.stop()
    }

    companion object : HttpClientBackendFactory {
        override operator fun invoke(block: HttpClientBackendConfig.() -> Unit): HttpClientBackend = JettyHttp2Backend()
    }
}