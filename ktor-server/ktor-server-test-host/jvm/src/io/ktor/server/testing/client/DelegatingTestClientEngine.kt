/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.testing.client

import io.ktor.client.engine.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.util.*
import kotlinx.coroutines.*
import kotlin.coroutines.*

internal class DelegatingTestClientEngine(
    override val config: DelegatingTestHttpClientConfig
) : HttpClientEngineBase("delegating-test-engine") {

    private val externalEngines = config.externalEngines.toMap()
    private val mainEngine = config.mainEngine
    private val mainEngineHostWithPort = config.mainEngineHostWithPort

    override val dispatcher = Dispatchers.IO

    override val supportedCapabilities = setOf<HttpClientEngineCapability<*>>(WebSocketCapability)

    private val clientJob: CompletableJob = Job()

    override val coroutineContext: CoroutineContext = dispatcher + clientJob

    @InternalAPI
    override suspend fun execute(data: HttpRequestData): HttpResponseData {
        val authority = data.url.protocolWithAuthority
        val hostWithPort = data.url.hostWithPort
        return if (externalEngines.containsKey(authority)) {
            externalEngines[authority]!!.execute(data)
        } else if (hostWithPort == mainEngineHostWithPort) {
            mainEngine.execute(data)
        } else {
            throw InvalidTestRequestException(authority, externalEngines.keys, mainEngineHostWithPort)
        }
    }


    override fun close() {
        clientJob.complete()
        mainEngine.close()
        externalEngines.values.forEach { it.close() }
    }

    companion object : HttpClientEngineFactory<DelegatingTestHttpClientConfig> {
        override fun create(block: DelegatingTestHttpClientConfig.() -> Unit): HttpClientEngine {
            val config = DelegatingTestHttpClientConfig().apply(block)
            return DelegatingTestClientEngine(config)
        }
    }
}

public class InvalidTestRequestException(
    authority: String,
    externalAuthorities: Set<String>,
    mainHostWithPort: String
) : IllegalArgumentException(
    "Can not resolve request to $authority. " +
        "Main app runs at $mainHostWithPort and external services are ${externalAuthorities.joinToString()}"
)

internal class DelegatingTestHttpClientConfig : HttpClientEngineConfig() {
    lateinit var mainEngineHostWithPort: String
    lateinit var mainEngine: TestHttpClientEngine
    val externalEngines = mutableMapOf<String, TestHttpClientEngine>()
}
