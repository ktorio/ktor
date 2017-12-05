package io.ktor.client.engine.apache

import io.ktor.client.call.*
import io.ktor.client.engine.*
import io.ktor.client.request.*
import io.ktor.client.utils.*
import kotlinx.coroutines.experimental.*
import org.apache.http.impl.nio.client.*


class ApacheEngine(private val config: ApacheEngineConfig) : HttpClientEngine {
    private val engine: CloseableHttpAsyncClient = prepareClient().apply { start() }
    private val dispatcher: CoroutineDispatcher = config.dispatcher ?: HTTP_CLIENT_DEFAULT_DISPATCHER

    override fun prepareRequest(builder: HttpRequestBuilder, call: HttpClientCall): HttpRequest =
            ApacheHttpRequest(call, engine, config, dispatcher, builder)

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
