/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.apache5

import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.client.tests.*
import io.ktor.network.sockets.*
import io.ktor.util.*
import io.ktor.utils.io.*
import kotlinx.coroutines.runBlocking
import org.apache.hc.client5.http.config.ConnectionConfig
import org.apache.hc.core5.util.Timeout
import org.junit.jupiter.api.Assertions.assertTrue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class Apache5HttpClientTest : HttpClientTest(Apache5) {

    @Test
    @OptIn(InternalAPI::class)
    fun testSocketTimeoutWithCustomConnectionManager() = runBlocking {
        val client = HttpClient(Apache5) {
            engine {
                configureConnectionManager {
                    setMaxConnPerRoute(1_000)
                    setMaxConnTotal(2_000)
                }
            }
            install(HttpTimeout) {
                socketTimeoutMillis = 1000
            }
        }

        assertFailsWith<ClosedByteChannelException> {
            client.prepareGet("http://localhost:$serverPort/sse/delay/2000").execute { response: HttpResponse ->
                val channel = response.bodyAsChannel()
                while (!channel.isClosedForRead) {
                    channel.readLineStrict()
                }
            }
        }.apply {
            assertTrue(rootCause is SocketTimeoutException)
        }
        Unit
    }

    @Test
    @OptIn(InternalAPI::class)
    fun testCustomTimeoutOverridesHttpTimeout() = runBlocking {
        val client = HttpClient(Apache5) {
            engine {
                configureConnectionManager {
                    setDefaultConnectionConfig(
                        ConnectionConfig.custom()
                            .setSocketTimeout(Timeout.ofMilliseconds(10000))
                            .build()
                    )
                }
            }
            install(HttpTimeout) {
                socketTimeoutMillis = 1000
            }
        }

        client.prepareGet("http://localhost:$serverPort/sse/delay/2000").execute { response: HttpResponse ->
            val channel = response.bodyAsChannel()
            assertEquals("data: hello", channel.readLineStrict())
        }
    }
}
