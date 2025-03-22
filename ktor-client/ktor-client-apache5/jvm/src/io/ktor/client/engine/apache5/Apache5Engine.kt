/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.apache5

import io.ktor.client.engine.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.sse.*
import io.ktor.client.request.*
import io.ktor.utils.io.*
import kotlinx.coroutines.Job
import org.apache.hc.client5.http.config.ConnectionConfig
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient
import org.apache.hc.client5.http.impl.async.HttpAsyncClientBuilder
import org.apache.hc.client5.http.impl.async.HttpAsyncClients
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder
import org.apache.hc.client5.http.ssl.ClientTlsStrategyBuilder
import org.apache.hc.core5.http.HttpHost
import org.apache.hc.core5.http.ssl.TLS
import org.apache.hc.core5.io.CloseMode
import org.apache.hc.core5.reactor.IOReactorConfig
import org.apache.hc.core5.ssl.SSLContexts
import org.apache.hc.core5.util.Timeout
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit

private const val MAX_CONNECTIONS_COUNT = 1000
private const val IO_THREAD_COUNT_DEFAULT = 4

@OptIn(InternalAPI::class)
internal class Apache5Engine(override val config: Apache5EngineConfig) : HttpClientEngineBase("ktor-apache") {

    override val supportedCapabilities = setOf(HttpTimeoutCapability, SSECapability)

    @Volatile
    private var engine: CloseableHttpAsyncClient? = null

    override suspend fun execute(data: HttpRequestData): HttpResponseData {
        val callContext = callContext()

        val engine = engine(data)

        val apacheRequest = ApacheRequestProducer(data, config, callContext)
        return engine.sendRequest(apacheRequest, callContext, data)
    }

    override fun close() {
        super.close()

        coroutineContext[Job]!!.invokeOnCompletion {
            engine?.close(CloseMode.IMMEDIATE)
        }
    }

    private fun engine(data: HttpRequestData): CloseableHttpAsyncClient {
        return engine ?: synchronized(this) {
            engine ?: HttpAsyncClients.custom().apply {
                val timeout = data.getCapabilityOrNull(HttpTimeoutCapability)
                setThreadFactory {
                    Thread(it, "Ktor-client-apache").apply {
                        isDaemon = true
                        setUncaughtExceptionHandler { _, _ -> }
                    }
                }
                disableAuthCaching()
                disableConnectionState()
                disableCookieManagement()

                val socketTimeoutMillis: Long = timeout?.socketTimeoutMillis ?: config.socketTimeout.toLong()
                val socketTimeout = if (socketTimeoutMillis == HttpTimeoutConfig.INFINITE_TIMEOUT_MS) {
                    null
                } else {
                    Timeout.of(
                        socketTimeoutMillis,
                        TimeUnit.MILLISECONDS
                    )
                }
                val connectTimeoutMillis: Long = timeout?.connectTimeoutMillis ?: config.connectTimeout
                val connectTimeout = if (connectTimeoutMillis == HttpTimeoutConfig.INFINITE_TIMEOUT_MS) {
                    0
                } else {
                    connectTimeoutMillis
                }

                setConnectionManager(
                    PoolingAsyncClientConnectionManagerBuilder.create()
                        .setMaxConnTotal(MAX_CONNECTIONS_COUNT)
                        .setMaxConnPerRoute(MAX_CONNECTIONS_COUNT)
                        .setTlsStrategy(
                            ClientTlsStrategyBuilder.create()
                                .setSslContext(config.sslContext ?: SSLContexts.createSystemDefault())
                                .setTlsVersions(TLS.V_1_3, TLS.V_1_2)
                                // TODO: Uncomment this line and remove apply after update to v5.5
                                //  https://github.com/apache/httpcomponents-client/commit/001eff70646c982c8c4a7c8a385d92f42579f2b5
                                // .setHostVerificationPolicy(config.sslHostnameVerificationPolicy)
                                .apply { setHostnameVerificationPolicy(config.sslHostnameVerificationPolicy) }
                                .build()
                        )
                        .setDefaultConnectionConfig(
                            ConnectionConfig.custom()
                                .setConnectTimeout(connectTimeout, TimeUnit.MILLISECONDS)
                                .setSocketTimeout(socketTimeout)
                                .build()
                        )
                        .build()
                )
                setIOReactorConfig(
                    IOReactorConfig.custom()
                        .setIoThreadCount(IO_THREAD_COUNT_DEFAULT)
                        .build()
                )

                setupProxy()

                with(config) { customClient() }
            }.build().also {
                engine = it
                it.start()
            }
        }
    }

    private fun HttpAsyncClientBuilder.setupProxy() {
        val proxy = config.proxy ?: return

        if (proxy.type() == Proxy.Type.DIRECT) {
            return
        }

        val address = proxy.address()
        check(proxy.type() == Proxy.Type.HTTP && address is InetSocketAddress) {
            "Only http proxy is supported for Apache engine."
        }

        setProxy(HttpHost.create("http://${address.hostName}:${address.port}"))
    }
}
