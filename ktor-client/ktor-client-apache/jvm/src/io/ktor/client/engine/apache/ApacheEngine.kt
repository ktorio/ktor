package io.ktor.client.engine.apache

import io.ktor.client.engine.*
import io.ktor.client.request.*
import org.apache.http.impl.nio.client.*
import org.apache.http.impl.nio.reactor.*

private const val MAX_CONNECTIONS_COUNT = 1000
private const val IO_THREAD_COUNT_DEFAULT = 4

internal class ApacheEngine(override val config: ApacheEngineConfig) : HttpClientJvmEngine("ktor-apache") {

    private val engine: CloseableHttpAsyncClient = prepareClient().apply { start() }

    override suspend fun execute(data: HttpRequestData): HttpResponseData {
        val callContext = createCallContext()
        val apacheRequest = ApacheRequestProducer(data, config, callContext)
        return engine.sendRequest(apacheRequest, callContext)
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
