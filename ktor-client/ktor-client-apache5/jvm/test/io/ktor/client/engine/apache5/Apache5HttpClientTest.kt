/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.apache5

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.client.test.base.*
import io.ktor.client.tests.*
import io.ktor.http.*
import io.ktor.test.*
import io.ktor.util.*
import io.ktor.utils.io.*
import org.apache.hc.client5.http.config.ConnectionConfig
import org.apache.hc.core5.util.Timeout
import java.net.SocketTimeoutException
import java.util.zip.GZIPInputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class Apache5HttpClientTest : HttpClientTest(Apache5) {

    @Test
    fun `does not decode compressed responses without ContentEncoding`() = runTest {
        HttpClient(Apache5).use { client ->
            val response = client.get("$TEST_SERVER/compression/gzip-precompressed")
            assertRawGzipResponse(response)
        }
    }

    @Test
    fun `request customization cannot enable response decompression`() = runTest {
        HttpClient(Apache5) {
            engine {
                customizeRequest {
                    setContentCompressionEnabled(true)
                }
            }
        }.use { client ->
            client.prepareGet("$TEST_SERVER/compression/gzip-precompressed").execute { response ->
                assertRawGzipResponse(response)
            }
        }
    }

    private suspend fun assertRawGzipResponse(response: HttpResponse) {
        assertEquals("gzip", response.headers[HttpHeaders.ContentEncoding])
        val body = response.body<ByteArray>()
        assertEquals(294L, response.contentLength())
        assertEquals(response.contentLength(), body.size.toLong())
        assertContentEquals(
            ByteArray(500) { it.toByte() },
            GZIPInputStream(body.inputStream()).use { it.readBytes() }
        )
    }

    @Test
    @OptIn(InternalAPI::class)
    fun testSocketTimeoutWithCustomConnectionManager() = runTest {
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
            assertIs<SocketTimeoutException>(rootCause)
        }
    }

    @Test
    @OptIn(InternalAPI::class)
    fun testCustomTimeoutOverridesHttpTimeout() = runTest {
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
