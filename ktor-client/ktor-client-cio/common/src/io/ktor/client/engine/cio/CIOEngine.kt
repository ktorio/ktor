/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.cio

import io.ktor.client.engine.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.sse.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.network.selector.*
import io.ktor.util.*
import io.ktor.util.collections.*
import io.ktor.util.network.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlin.coroutines.CoroutineContext

@OptIn(InternalAPI::class, DelicateCoroutinesApi::class)
internal class CIOEngine(
    override val config: CIOEngineConfig
) : HttpClientEngineBase("ktor-cio") {

    override val supportedCapabilities =
        setOf(HttpTimeoutCapability, WebSocketCapability, WebSocketExtensionsCapability, SSECapability)

    private val endpoints = ConcurrentMap<String, Endpoint>()

    private val selectorManager = SelectorManager(dispatcher)

    private val connectionFactory = ConnectionFactory(
        selectorManager,
        config.maxConnectionsCount,
        config.endpoint.maxConnectionsPerRoute
    )

    private val requestsJob: CoroutineContext

    override val coroutineContext: CoroutineContext

    private val proxy: ProxyConfig? = when (val type = config.proxy?.type) {
        ProxyType.SOCKS,
        null -> null

        ProxyType.HTTP -> config.proxy
        else -> throw IllegalStateException("CIO engine does not currently support $type proxies.")
    }

    init {
        val parentContext = super.coroutineContext
        val parent = parentContext[Job]!!

        requestsJob = SilentSupervisor(parent)

        val requestField = requestsJob
        coroutineContext = parentContext + requestField

        val requestJob = requestField[Job]!!
        val selector = selectorManager

        GlobalScope.launch(parentContext, start = CoroutineStart.ATOMIC) {
            try {
                requestJob.join()
            } finally {
                selector.close()
                selector.coroutineContext[Job]!!.join()
            }
        }
    }

    override suspend fun execute(data: HttpRequestData): HttpResponseData {
        val callContext = callContext()

        while (coroutineContext.isActive) {
            val endpoint = selectEndpoint(data.url, proxy)

            try {
                return endpoint.execute(data, callContext)
            } catch (_: ClosedSendChannelException) {
                continue
            } finally {
                if (!coroutineContext.isActive) {
                    endpoint.close()
                }
            }
        }

        throw ClientEngineClosedException()
    }

    override fun close() {
        super.close()

        endpoints.forEach { (_, endpoint) ->
            endpoint.close()
        }

        (requestsJob[Job] as CompletableJob).complete()
    }

    private fun selectEndpoint(url: Url, proxy: ProxyConfig?): Endpoint {
        val host: String
        val port: Int
        val protocol: URLProtocol = url.protocol

        if (proxy != null) {
            val proxyAddress = proxy.resolveAddress()
            host = proxyAddress.hostname
            port = proxyAddress.port
        } else {
            host = url.host
            port = url.port
        }

        val endpointId = "$host:$port:$protocol"

        return endpoints.computeIfAbsent(endpointId) {
            val secure = (protocol.isSecure())
            Endpoint(
                host,
                port,
                proxy,
                secure,
                config,
                connectionFactory,
                coroutineContext,
                onDone = { endpoints.remove(endpointId) }
            )
        }
    }
}
