/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.java

import io.ktor.client.engine.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.sse.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.utils.io.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.cancel
import kotlinx.coroutines.job
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.http.HttpClient
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit

public class JavaHttpEngine(override val config: JavaHttpConfig) : HttpClientEngineBase("ktor-java") {

    private val protocolVersion = config.protocolVersion

    public override val supportedCapabilities: Set<HttpClientEngineCapability<*>> =
        setOf(HttpTimeoutCapability, WebSocketCapability, SSECapability)

    private var httpClient: HttpClient? = null

    init {
        coroutineContext.job.invokeOnCompletion {
            httpClient = null
        }
    }

    @OptIn(InternalAPI::class)
    override suspend fun execute(data: HttpRequestData): HttpResponseData {
        val engine = getJavaHttpClient(data)
        val callContext = callContext()

        return try {
            if (data.isUpgradeRequest()) {
                engine.executeWebSocketRequest(callContext, data)
            } else {
                engine.executeHttpRequest(callContext, data) ?: throw kotlinx.coroutines.CancellationException(
                    "Request was cancelled"
                )
            }
        } catch (cause: Throwable) {
            callContext.cancel(CancellationException("Failed to execute request", cause))
            throw cause
        }
    }

    private fun getJavaHttpClient(data: HttpRequestData): HttpClient {
        return httpClient ?: synchronized(this) {
            httpClient ?: HttpClient.newBuilder().apply {
                version(protocolVersion)
                executor(dispatcher.asExecutor())

                apply(config.config)

                setupProxy()

                data.getCapabilityOrNull(HttpTimeoutCapability)?.let { timeoutAttribute ->
                    timeoutAttribute.connectTimeoutMillis?.let {
                        if (!isTimeoutInfinite(it)) connectTimeout(Duration.ofMillis(it))
                    }
                }
            }.build().also {
                httpClient = it
            }
        }
    }

    private fun HttpClient.Builder.setupProxy() {
        val proxy = config.proxy ?: return
        val proxyType = proxy.type()
        if (proxyType == Proxy.Type.DIRECT) {
            proxy(HttpClient.Builder.NO_PROXY)
            return
        }

        val address = proxy.address()
        // See: https://bugs.openjdk.org/browse/JDK-8214516
        check(proxyType == Proxy.Type.HTTP && address is InetSocketAddress) {
            "Only HTTP proxy is supported for Java HTTP engine, but configured $proxyType."
        }

        proxy(ProxySelector.of(address))
    }
}

internal fun isTimeoutInfinite(timeoutMs: Long, now: Instant = Instant.now()): Boolean {
    if (timeoutMs == HttpTimeoutConfig.INFINITE_TIMEOUT_MS) return true
    return try {
        // Check that timeout end date as the number of milliseconds can fit Long type
        now.plus(timeoutMs, ChronoUnit.MILLIS).toEpochMilli()
        false
    } catch (_: ArithmeticException) {
        true
    }
}
