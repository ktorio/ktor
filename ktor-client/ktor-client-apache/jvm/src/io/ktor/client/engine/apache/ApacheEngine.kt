/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.apache

import io.ktor.client.engine.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.sse.*
import io.ktor.client.request.*
import io.ktor.utils.io.*
import kotlinx.coroutines.Job
import org.apache.http.HttpHost
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder
import org.apache.http.impl.nio.client.HttpAsyncClients
import org.apache.http.impl.nio.reactor.IOReactorConfig
import java.net.InetSocketAddress
import java.net.Proxy

private const val MAX_CONNECTIONS_COUNT = 1000
private const val IO_THREAD_COUNT_DEFAULT = 4

@OptIn(InternalKtorApi::class)
internal class ApacheEngine(override val config: ApacheEngineConfig) : HttpClientEngineBase("ktor-apache") {

    override val supportedCapabilities = setOf(HttpTimeoutCapability, SSECapability)

    private val engine: CloseableHttpAsyncClient = prepareClient().apply { start() }

    override suspend fun execute(data: HttpRequestData): HttpResponseData {
        val callContext = callContext()

        val apacheRequest = ApacheRequestProducer(data, config, callContext)
        return engine.sendRequest(apacheRequest, callContext, data)
    }

    override fun close() {
        super.close()

        coroutineContext[Job]!!.invokeOnCompletion {
            engine.close()
        }
    }

    private fun prepareClient(): CloseableHttpAsyncClient {
        val clientBuilder = HttpAsyncClients.custom()
        with(clientBuilder) {
            setThreadFactory {
                Thread(it, "Ktor-client-apache").apply {
                    isDaemon = true
                    setUncaughtExceptionHandler { _, _ -> }
                }
            }
            disableAuthCaching()
            disableConnectionState()
            disableCookieManagement()
            setMaxConnPerRoute(MAX_CONNECTIONS_COUNT)
            setMaxConnTotal(MAX_CONNECTIONS_COUNT)
            setDefaultIOReactorConfig(
                IOReactorConfig.custom()
                    .setIoThreadCount(IO_THREAD_COUNT_DEFAULT)
                    .build()
            )

            setupProxy()
        }

        with(config) {
            clientBuilder.customClient()
        }

        config.sslContext?.let { clientBuilder.setSSLContext(it) }
        return clientBuilder.build()!!
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
