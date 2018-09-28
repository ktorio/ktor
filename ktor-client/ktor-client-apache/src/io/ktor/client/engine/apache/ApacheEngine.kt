package io.ktor.client.engine.apache

import io.ktor.client.call.*
import io.ktor.client.engine.*
import io.ktor.client.request.*
import org.apache.http.impl.nio.client.*
import org.apache.http.impl.nio.reactor.*

private const val MAX_CONNECTIONS_COUNT = 1000
private const val IO_THREAD_COUNT_DEFAULT = 4

internal class ApacheEngine(override val config: ApacheEngineConfig) : HttpClientJvmEngine("ktor-apache") {

    private val engine: CloseableHttpAsyncClient = prepareClient().apply { start() }

    override suspend fun execute(call: HttpClientCall, data: HttpRequestData): HttpEngineCall {
        val callContext = createCallContext()
        val engineRequest = ApacheHttpRequest(call, data)
        val apacheRequest = ApacheRequestProducer(data, config, engineRequest.content, callContext)
        val engineResponse = engine.sendRequest(call, apacheRequest, callContext)

        return HttpEngineCall(engineRequest, engineResponse)
    }

    override fun close() {
        super.close()
        try {
            engine.close()
        } catch (_: Throwable) {
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
