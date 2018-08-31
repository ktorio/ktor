package io.ktor.client.engine.apache

import io.ktor.client.call.*
import io.ktor.client.engine.*
import io.ktor.client.request.*
import io.ktor.client.utils.*
import kotlinx.coroutines.*
import org.apache.http.impl.nio.client.*
import org.apache.http.impl.nio.reactor.*

private const val MAX_CONNECTIONS_COUNT = 1000
private const val IO_THREAD_COUNT_DEFAULT = 4

internal class ApacheEngine(override val config: ApacheEngineConfig) : HttpClientEngine {
    private val engine: CloseableHttpAsyncClient = prepareClient().apply { start() }
    override val dispatcher: CoroutineDispatcher = config.dispatcher ?: HTTP_CLIENT_DEFAULT_DISPATCHER

    override suspend fun execute(call: HttpClientCall, data: HttpRequestData): HttpEngineCall {
        val request = ApacheHttpRequest(call, engine, config, data, dispatcher)
        val response = request.execute()

        return HttpEngineCall(request, response)
    }

    override fun close() {
        try {
            engine.close()
        } catch (cause: Throwable) {
        }
    }

    private fun prepareClient(): CloseableHttpAsyncClient {
        val clientBuilder = HttpAsyncClients.custom()
        with(clientBuilder) {
            setThreadFactory { Thread(it, "Ktor-client-apache").apply { isDaemon = true } }
            disableAuthCaching()
            disableConnectionState()
            disableCookieManagement()
            setDefaultIOReactorConfig(IOReactorConfig.custom().apply {
                setMaxConnPerRoute(MAX_CONNECTIONS_COUNT)
                setMaxConnTotal(MAX_CONNECTIONS_COUNT)
                setIoThreadCount(IO_THREAD_COUNT_DEFAULT)
            }.build())
        }

        with(config) {
            clientBuilder.customClient()
        }

        config.sslContext?.let { clientBuilder.setSSLContext(it) }
        return clientBuilder.build()!!
    }
}
