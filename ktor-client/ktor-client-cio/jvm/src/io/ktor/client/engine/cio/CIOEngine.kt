/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.cio

import io.ktor.client.engine.*
import io.ktor.client.request.*
import io.ktor.client.utils.*
import io.ktor.http.*
import io.ktor.network.selector.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import java.io.*
import java.net.*
import java.util.concurrent.*

internal class CIOEngine(override val config: CIOEngineConfig) : HttpClientEngineBase("ktor-cio") {

    override val dispatcher by lazy { Dispatchers.fixedThreadPoolDispatcher(config.threadsCount, "ktor-cio-thread-%d") }

    private val endpoints = ConcurrentHashMap<String, Endpoint>()

    @UseExperimental(InternalCoroutinesApi::class)
    private val selectorManager by lazy { ActorSelectorManager(dispatcher) }

    private val connectionFactory = ConnectionFactory(selectorManager, config.maxConnectionsCount)

    private val proxy = when (val type = config.proxy?.type()) {
        Proxy.Type.DIRECT,
        null -> null
        Proxy.Type.HTTP -> config.proxy
        else -> throw IllegalStateException("Proxy of type $type is unsupported by CIO engine.")
    }

    override suspend fun execute(data: HttpRequestData): HttpResponseData {
        val callContext = callContext()

        while (coroutineContext.isActive) {
            val endpoint = selectEndpoint(data.url, proxy)

            try {
                return endpoint.execute(data, callContext)
            } catch (cause: ClosedSendChannelException) {
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
        endpoints.forEach { (_, endpoint) -> endpoint.close() }

        coroutineContext[Job]!!.invokeOnCompletion {
            selectorManager.close()
        }
    }

    private fun selectEndpoint(url: Url, proxy: ProxyConfig?): Endpoint {
        val host: String
        val port: Int
        val protocol: URLProtocol = url.protocol

        if (proxy != null) {
            val proxyAddress = proxy.address() as InetSocketAddress
            host = proxyAddress.hostName
            port = proxyAddress.port
        } else {
            host = url.host
            port = url.port
        }

        val endpointId = "$host:$port:$protocol"

        return endpoints.computeIfAbsentWeak(endpointId) {
            val secure = (protocol.isSecure())
            Endpoint(
                host, port, proxy != null, secure,
                config,
                connectionFactory, coroutineContext,
                onDone = { endpoints.remove(endpointId) }
            )
        }
    }
}

@Suppress("KDocMissingDocumentation")
@Deprecated("Use ClientEngineClosedException instead", replaceWith = ReplaceWith("ClientEngineClosedException"))
class ClientClosedException(cause: Throwable? = null) : IllegalStateException("Client already closed", cause)

private fun <K : Any, V : Closeable> ConcurrentHashMap<K, V>.computeIfAbsentWeak(key: K, block: (K) -> V): V {
    get(key)?.let { return it }

    val newValue = block(key)
    val result = putIfAbsent(key, newValue)
    if (result != null) {
        newValue.close()
        return result
    }

    return newValue
}
