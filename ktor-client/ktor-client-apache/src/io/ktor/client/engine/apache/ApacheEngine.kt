package io.ktor.client.engine.apache

import io.ktor.client.call.*
import io.ktor.client.engine.*
import io.ktor.client.request.*
import io.ktor.client.request.HttpRequest
import org.apache.http.*
import org.apache.http.impl.nio.client.*
import java.io.*


internal data class ApacheResponse(val engineResponse: HttpResponse, val responseReader: Closeable)

class ApacheEngine(private val config: ApacheEngineConfig) : HttpClientEngine {
    private val engine: CloseableHttpAsyncClient = prepareClient().apply { start() }

    override fun prepareRequest(builder: HttpRequestBuilder, call: HttpClientCall): HttpRequest =
            ApacheHttpRequest(call, engine, config, builder)

    override fun close() {
        engine.close()
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
}
