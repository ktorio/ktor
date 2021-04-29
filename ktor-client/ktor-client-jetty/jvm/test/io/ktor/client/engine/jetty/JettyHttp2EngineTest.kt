/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.engine.jetty

import io.ktor.client.*
import io.ktor.client.features.*
import io.ktor.client.request.*
import io.ktor.client.tests.utils.*
import io.ktor.test.dispatcher.*
import io.ktor.utils.io.errors.*
import kotlin.test.*

class JettyHttp2EngineTest {

    @Test
    fun testConnectingToNonHttp2Server() = testSuspend {
        HttpClient(Jetty).use { client ->
            assertFailsWith<IOException> {
                client.get<String>("$TEST_SERVER/content/hello")
            }
        }
    }

    @Test
    fun testReuseClientsInCache() = testSuspend {
        val engine = JettyHttp2Engine(JettyEngineConfig())

        val timeoutConfig11 = HttpTimeout.HttpTimeoutCapabilityConfiguration(
            requestTimeoutMillis = 1,
            connectTimeoutMillis = 1,
            socketTimeoutMillis = 1
        )
        val timeoutConfig12 = HttpTimeout.HttpTimeoutCapabilityConfiguration(
            requestTimeoutMillis = 1,
            connectTimeoutMillis = 1,
            socketTimeoutMillis = 1
        )
        val timeoutConfig21 = HttpTimeout.HttpTimeoutCapabilityConfiguration(
            requestTimeoutMillis = 2,
            connectTimeoutMillis = 2,
            socketTimeoutMillis = 2
        )
        val timeoutConfig22 = HttpTimeout.HttpTimeoutCapabilityConfiguration(
            requestTimeoutMillis = 2,
            connectTimeoutMillis = 2,
            socketTimeoutMillis = 2
        )

        val request11 = HttpRequestBuilder().apply {
            setCapability(HttpTimeout, timeoutConfig11)
        }.build()
        engine.getOrCreateClient(request11)
        assertEquals(1, engine.clientCache.size)

        val request12 = HttpRequestBuilder().apply {
            setCapability(HttpTimeout, timeoutConfig12)
        }.build()
        engine.getOrCreateClient(request12)
        assertEquals(1, engine.clientCache.size)

        val request21 = HttpRequestBuilder().apply {
            setCapability(HttpTimeout, timeoutConfig21)
        }.build()
        engine.getOrCreateClient(request21)
        assertEquals(2, engine.clientCache.size)

        val request22 = HttpRequestBuilder().apply {
            setCapability(HttpTimeout, timeoutConfig22)
        }.build()
        engine.getOrCreateClient(request22)
        assertEquals(2, engine.clientCache.size)
    }
}
