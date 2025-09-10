/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.okhttp

import io.ktor.client.*
import io.ktor.client.network.sockets.*
import io.ktor.client.plugins.sse.*
import io.ktor.client.test.base.*
import io.ktor.client.tests.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.jupiter.api.assertInstanceOf
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

class OkHttpHttpClientTest : HttpClientTest(OkHttp) {
    @Test
    fun testCancelSseRequestIncomingCollect() = runTest {
        val okHttpClient = OkHttpClient()

        val client = HttpClient(OkHttp) {
            engine { preconfigured = okHttpClient }
            install(SSE)
        }
        var callJob: Job? = null
        var request: Job? = null
        request = launch {
            client.sse("${TEST_SERVER}/sse/hello?times=20&interval=100") {
                callJob = call.request.coroutineContext.job
                request?.cancel() // Cancel the request once the connection is open.
                incoming.collect() // Collect all messages.
            }
            fail("Request should be cancelled.")
        }
        request.join()
        callJob?.join()

        okHttpClient.connectionPool.evictAll() // Make sure idle connections are removed.
        assertEquals(0, okHttpClient.connectionPool.connectionCount())
    }

    @Test
    fun testCancelSseRequestWithDelay() = runTest {
        val okHttpClient = OkHttpClient()

        val client = HttpClient(OkHttp) {
            engine { preconfigured = okHttpClient }
            install(SSE)
        }
        var callJob: Job? = null
        var request: Job? = null
        request = launch {
            client.sse("${TEST_SERVER}/sse/hello?times=20&interval=100") {
                callJob = call.request.coroutineContext.job
                request?.cancel() // Cancel the request once the connection is open.
                delay(1) // Never read from incoming.
            }
            fail("Request should be cancelled.")
        }
        request.join()
        callJob?.join()

        okHttpClient.connectionPool.evictAll() // Make sure idle connections are removed.
        assertEquals(0, okHttpClient.connectionPool.connectionCount())
    }

    @Test
    fun testSSESessionTimeout() {
        val okHttpClient = OkHttpClient.Builder().apply {
            readTimeout(1L, TimeUnit.SECONDS)
        }.build()

        HttpClient(OkHttp) {
            engine { preconfigured = okHttpClient }
            install(SSE)
        }.use { client ->
            runTest {
                try {
                    client.sse("$TEST_SERVER/sse/hello?delay=10000") {
                        incoming.collect()
                        fail("Request should error.")
                    }
                } catch (e: SSEClientException) {
                    assertInstanceOf<SocketTimeoutException>(e.cause)
                }
            }
        }
    }
}
