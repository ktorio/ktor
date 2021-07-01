/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.jetty

import io.ktor.client.engine.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.utils.*
import io.ktor.util.*
import kotlinx.coroutines.*
import org.eclipse.jetty.http2.client.*
import org.eclipse.jetty.util.thread.*

internal class JettyHttp2Engine(
    override val config: JettyEngineConfig
) : HttpClientEngineBase("ktor-jetty") {

    override val dispatcher: CoroutineDispatcher by lazy {
        Dispatchers.clientDispatcher(
            config.threadsCount,
            "ktor-jetty-dispatcher"
        )
    }

    override val supportedCapabilities = setOf(HttpTimeout)

    /**
     * Cache that keeps least recently used [HTTP2Client] instances. Set "0" to avoid caching.
     */
    internal val clientCache = createLRUCache(::createJettyClient, HTTP2Client::stop, config.clientCacheSize)

    override suspend fun execute(data: HttpRequestData): HttpResponseData {
        val callContext = callContext()
        val jettyClient = getOrCreateClient(data)

        return data.executeRequest(jettyClient, config, callContext)
    }

    /** Only for tests */
    internal fun getOrCreateClient(data: HttpRequestData): HTTP2Client {
        return clientCache[data.getCapabilityOrNull(HttpTimeout)]
            ?: error("Http2Client can't be constructed because HttpTimeout plugin is not installed")
    }

    override fun close() {
        super.close()

        coroutineContext[Job]!!.invokeOnCompletion {
            clientCache.forEach { (_, client) -> client.stop() }
        }
    }

    private fun createJettyClient(timeoutExtension: HttpTimeout.HttpTimeoutCapabilityConfiguration?): HTTP2Client =
        HTTP2Client().apply {
            addBean(config.sslContextFactory)
            check(config.proxy == null) { "Proxy unsupported in Jetty engine." }

            executor = QueuedThreadPool().apply {
                name = "ktor-jetty-client-qtp"
            }

            setupTimeoutAttributes(timeoutExtension)

            start()
        }
}

/**
 * Update [HTTP2Client] to use connect and socket timeouts specified by [HttpTimeout] plugin.
 */
private fun HTTP2Client.setupTimeoutAttributes(timeoutAttributes: HttpTimeout.HttpTimeoutCapabilityConfiguration?) {
    timeoutAttributes?.connectTimeoutMillis?.let { connectTimeout = it }
    timeoutAttributes?.socketTimeoutMillis?.let { idleTimeout = it }
}
