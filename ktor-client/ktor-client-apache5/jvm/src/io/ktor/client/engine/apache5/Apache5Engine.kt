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

        val engine = engine()

        val apacheRequest = ApacheRequestProducer(data, config, callContext)
        return engine.sendRequest(apacheRequest, callContext, data)
    }

    override fun close() {
        super.close()

        coroutineContext[Job]!!.invokeOnCompletion {
            engine?.close(CloseMode.IMMEDIATE)
        }
    }

    private fun engine(): CloseableHttpAsyncClient {
        return engine ?: synchronized(this) {
            engine ?: HttpAsyncClients.custom().apply {
                setThreadFactory {
                    Thread(it, "Ktor-client-apache").apply {
                        isDaemon = true
                        setUncaughtExceptionHandler { _, _ -> }
                    }
                }
                disableAuthCaching()
                disableConnectionState()
                disableCookieManagement()

                setConnectionManager(
                    PoolingAsyncClientConnectionManagerBuilder.create()
                        .setMaxConnTotal(MAX_CONNECTIONS_COUNT)
                        .setMaxConnPerRoute(MAX_CONNECTIONS_COUNT)
                        .setTlsStrategy(
                            ClientTlsStrategyBuilder.create()
                                .setSslContext(config.sslContext ?: SSLContexts.createSystemDefault())
                                .setTlsVersions(TLS.V_1_3, TLS.V_1_2)
                                .setHostVerificationPolicy(config.sslHostnameVerificationPolicy)
                                .buildAsync()
                        )
                        .setDefaultConnectionConfig(
                            ConnectionConfig.custom()
                                .setConnectTimeout(config.connectTimeout.toTimeout())
                                .setSocketTimeout(config.socketTimeout, TimeUnit.MILLISECONDS)
                                .build()
                        )
                        .apply { config.dnsResolver?.let { setDnsResolver(it) } }
                        .apply(config.configureConnectionManager)
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
        val proxyType = proxy.type()
        if (proxyType == Proxy.Type.DIRECT) return

        val address = proxy.address()
        check(proxyType == Proxy.Type.HTTP && address is InetSocketAddress) {
            "Only HTTP proxy is supported for Apache engine, but configured $proxyType."
        }

        setProxy(HttpHost.create("http://${address.hostName}:${address.port}"))
    }
}
