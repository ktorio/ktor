/*
 * Copyright 2014-2020 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.java

import io.ktor.client.engine.*
import io.ktor.client.features.*
import io.ktor.client.features.websocket.*
import io.ktor.client.request.*
import io.ktor.util.*
import kotlinx.atomicfu.*
import kotlinx.coroutines.*
import java.io.*
import java.net.*
import java.net.http.*
import java.time.*
import java.util.concurrent.*
import kotlin.coroutines.*

@InternalAPI
public class JavaHttpEngine(override val config: JavaHttpConfig) : HttpClientEngineBase("ktor-java") {

    private val executorThreadCounter = atomic(0L)

    /**
     * Exposed for tests only.
     */
    internal val executor by lazy {
        Executors.newFixedThreadPool(config.threadsCount) {
            val number = executorThreadCounter.getAndIncrement()
            Thread(it, "ktor-client-java-$number").apply {
                isDaemon = true
                setUncaughtExceptionHandler { _, _ -> }
            }
        }
    }

    public override val dispatcher: CoroutineDispatcher by lazy {
        executor.asCoroutineDispatcher()
    }

    public override val supportedCapabilities: Set<HttpClientEngineCapability<*>> =
        setOf(HttpTimeout, WebSocketCapability)

    override val coroutineContext: CoroutineContext

    private val requestsJob = SilentSupervisor(super.coroutineContext[Job])

    private var httpClient: HttpClient? = null

    init {
        coroutineContext = super.coroutineContext + requestsJob + CoroutineName("java-engine")

        @OptIn(ExperimentalCoroutinesApi::class)
        GlobalScope.launch(super.coroutineContext, start = CoroutineStart.ATOMIC) {
            try {
                requestsJob[Job]!!.join()
            } finally {
                httpClient = null
                (dispatcher as Closeable).close()
                executor.shutdown()
            }
        }
    }

    override suspend fun execute(data: HttpRequestData): HttpResponseData {
        val engine = getJavaHttpClient(data)
        val callContext = callContext()

        return if (data.isUpgradeRequest()) {
            engine.executeWebSocketRequest(callContext, data)
        } else {
            engine.executeHttpRequest(callContext, data)
        }
    }

    override fun close() {
        super.close()

        (requestsJob[Job] as CompletableJob).complete()
    }

    private fun getJavaHttpClient(data: HttpRequestData): HttpClient {
        return httpClient ?: synchronized(this) {
            httpClient ?: HttpClient.newBuilder().apply {
                version(HttpClient.Version.HTTP_1_1)
                executor(executor)

                apply(config.config)

                setupProxy()

                data.getCapabilityOrNull(HttpTimeout)?.let { timeoutAttribute ->
                    timeoutAttribute.connectTimeoutMillis?.let {
                        connectTimeout(Duration.ofMillis(it))
                    }
                }
            }.build().also {
                httpClient = it
            }
        }
    }

    private fun HttpClient.Builder.setupProxy() {
        val proxy = config.proxy ?: return

        when (val type = proxy.type()) {
            Proxy.Type.SOCKS,
            Proxy.Type.HTTP -> {
                val address = proxy.address()

                check(address is InetSocketAddress) {
                    "Only http proxy is supported for Java HTTP engine."
                }

                proxy(ProxySelector.of(address))
            }
            Proxy.Type.DIRECT -> proxy(HttpClient.Builder.NO_PROXY)
            else -> throw IllegalStateException("Proxy of type $type is unsupported by Java HTTP engine.")
        }
    }
}
