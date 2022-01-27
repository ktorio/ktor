/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.testing.client

import io.ktor.client.engine.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.*
import io.ktor.server.testing.internal.*
import io.ktor.util.*
import io.ktor.utils.io.concurrent.*
import kotlinx.coroutines.*
import kotlin.coroutines.*

internal class DelegatingTestClientEngine(
    override val config: DelegatingTestHttpClientConfig
) : HttpClientEngineBase("delegating-test-engine") {

    override val dispatcher = Dispatchers.IOBridge
    override val supportedCapabilities = setOf<HttpClientEngineCapability<*>>(WebSocketCapability)

    private val appEngine by lazy(config.appEngineProvider)
    private val externalEngines by lazy {
        val engines = mutableMapOf<String, TestHttpClientEngine>()
        config.externalApplicationsProvider().forEach { (authority, testApplication) ->
            engines[authority] = TestHttpClientEngine(
                TestHttpClientConfig().apply { app = testApplication.engine }
            )
        }
        engines.toMap()
    }
    private val mainEngine by lazy {
        TestHttpClientEngine(TestHttpClientConfig().apply { app = appEngine })
    }
    private val mainEngineHostWithPort by lazy {
        runBlocking { appEngine.resolvedConnectors().first().let { "${it.host}:${it.port}" } }
    }

    private val clientJob: CompletableJob = Job(config.parentJob)

    override val coroutineContext: CoroutineContext = dispatcher + clientJob

    @InternalAPI
    override suspend fun execute(data: HttpRequestData): HttpResponseData {
        val authority = data.url.protocolWithAuthority
        val hostWithPort = data.url.hostWithPort
        return when {
            externalEngines.containsKey(authority) -> {
                externalEngines[authority]!!.execute(data)
            }
            hostWithPort == mainEngineHostWithPort -> {
                mainEngine.execute(data)
            }
            else -> {
                throw InvalidTestRequestException(authority, externalEngines.keys, mainEngineHostWithPort)
            }
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

/**
 * Thrown when a request is made to an unknown resource
 */
public class InvalidTestRequestException(
    authority: String,
    externalAuthorities: Set<String>,
    mainHostWithPort: String
) : IllegalArgumentException(
    "Can not resolve request to $authority. " +
        "Main app runs at $mainHostWithPort and external services are ${externalAuthorities.joinToString()}"
)

internal class DelegatingTestHttpClientConfig : HttpClientEngineConfig() {
    lateinit var externalApplicationsProvider: () -> Map<String, TestApplication>
    lateinit var appEngineProvider: () -> TestApplicationEngine
    lateinit var parentJob: Job
}
