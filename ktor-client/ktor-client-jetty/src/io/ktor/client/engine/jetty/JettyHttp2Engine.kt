package io.ktor.client.engine.jetty

import io.ktor.client.call.*
import io.ktor.client.engine.*
import io.ktor.client.request.*
import io.ktor.client.utils.*
import org.eclipse.jetty.http2.api.*
import org.eclipse.jetty.http2.client.*
import java.net.*


class JettyHttp2Engine(config: JettyEngineConfig) : HttpClientEngine {
    private val sslContextFactory = config.sslContextFactory
    private val jettyClient = HTTP2Client().apply {
        addBean(sslContextFactory)
        start()
    }

    private val dispatcher = config.dispatcher ?: HTTP_CLIENT_DEFAULT_DISPATCHER

    override fun prepareRequest(builder: HttpRequestBuilder, call: HttpClientCall): HttpRequest =
            JettyHttpRequest(call, this, dispatcher, builder.build())

    suspend fun connect(host: String, port: Int): Session {
        return withPromise { promise ->
            jettyClient.connect(sslContextFactory, InetSocketAddress(host, port), Session.Listener.Adapter(), promise)
        }
    }

    override fun close() {
        jettyClient.stop()
    }
}
