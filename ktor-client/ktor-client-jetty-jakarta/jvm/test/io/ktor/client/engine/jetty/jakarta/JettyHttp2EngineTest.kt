/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.jetty.jakarta

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.test.base.*
import io.ktor.client.utils.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.test.dispatcher.*
import io.ktor.util.*
import io.ktor.utils.io.*
import kotlinx.coroutines.Job
import kotlinx.io.IOException
import org.eclipse.jetty.http.HttpHeader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class JettyHttp2EngineTest {

    @OptIn(InternalAPI::class)
    @Test
    fun `KTOR-7416 custom Host header overrides default in headers frame`() {
        val requestData = HttpRequestData(
            Url("http://127.0.0.1:8080/test"),
            HttpMethod.Get,
            Headers.build {
                append(HttpHeaders.Host, "CustomHost")
            },
            EmptyContent,
            Job(),
            Attributes()
        )

        val headersFrame = requestData.prepareHeadersFrame()
        val authority = (headersFrame.metaData as org.eclipse.jetty.http.MetaData.Request).httpURI.authority

        assertEquals("CustomHost", authority)
    }

    @Test
    fun testConnectingToNonHttp2Server() = testSuspend {
        HttpClient(Jetty).use { client ->
            assertFailsWith<IOException> {
                client.get("$TEST_SERVER/content/hello").body<String>()
            }
        }
    }

    @Test
    fun testReuseClientsInCache() = testSuspend {
        val engine = JettyHttp2Engine(JettyEngineConfig())

        val timeoutConfig11 = HttpTimeoutConfig(
            requestTimeoutMillis = 1,
            connectTimeoutMillis = 1,
            socketTimeoutMillis = 1
        )
        val timeoutConfig12 = HttpTimeoutConfig(
            requestTimeoutMillis = 1,
            connectTimeoutMillis = 1,
            socketTimeoutMillis = 1
        )
        val timeoutConfig21 = HttpTimeoutConfig(
            requestTimeoutMillis = 2,
            connectTimeoutMillis = 2,
            socketTimeoutMillis = 2
        )
        val timeoutConfig22 = HttpTimeoutConfig(
            requestTimeoutMillis = 2,
            connectTimeoutMillis = 2,
            socketTimeoutMillis = 2
        )

        val request11 = HttpRequestBuilder().apply {
            setCapability(HttpTimeoutCapability, timeoutConfig11)
        }.build()
        engine.getOrCreateClient(request11)
        assertEquals(1, engine.clientCache.size)

        val request12 = HttpRequestBuilder().apply {
            setCapability(HttpTimeoutCapability, timeoutConfig12)
        }.build()
        engine.getOrCreateClient(request12)
        assertEquals(1, engine.clientCache.size)

        val request21 = HttpRequestBuilder().apply {
            setCapability(HttpTimeoutCapability, timeoutConfig21)
        }.build()
        engine.getOrCreateClient(request21)
        assertEquals(2, engine.clientCache.size)

        val request22 = HttpRequestBuilder().apply {
            setCapability(HttpTimeoutCapability, timeoutConfig22)
        }.build()
        engine.getOrCreateClient(request22)
        assertEquals(2, engine.clientCache.size)
    }
}
