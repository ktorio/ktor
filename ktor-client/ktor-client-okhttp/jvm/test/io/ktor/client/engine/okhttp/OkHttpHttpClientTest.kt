/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.okhttp

import io.ktor.client.*
import io.ktor.client.plugins.sse.*
import io.ktor.client.test.base.*
import io.ktor.client.tests.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

class OkHttpHttpClientTest : HttpClientTest(OkHttp) {
    @Test
    fun testCancelSseRequestIncomingCollect() {
        val okHttpClient = OkHttpClient()

        HttpClient(OkHttp) {
            engine { preconfigured = okHttpClient }
            install(SSE)
        }.use { client ->
            runTest {
                var request: Job? = null
                request = launch {
                    client.sse("${TEST_SERVER}/sse/hello?times=20&interval=100") {
                        request?.cancel() // Cancel the request once the connection is open.
                        incoming.collect() // Collect all messages.
                    }
                    fail("Request should be cancelled.")
                }
                request.join()
            }
        }

        okHttpClient.connectionPool.evictAll() // Make sure idle connections are removed.
        assertEquals(0, okHttpClient.connectionPool.connectionCount())
    }

    @Test
    fun testCancelSseRequestWithDelay() {
        val okHttpClient = OkHttpClient()

        HttpClient(OkHttp) {
            engine { preconfigured = okHttpClient }
            install(SSE)
        }.use { client ->
            runTest {
                var request: Job? = null
                request = launch {
                    client.sse("${TEST_SERVER}/sse/hello?times=20&interval=100") {
                        request?.cancel() // Cancel the request once the connection is open.
                        delay(1) // Never read from incoming.
                    }
                    fail("Request should be cancelled.")
                }
                request.join()
            }
        }

        okHttpClient.connectionPool.evictAll() // Make sure idle connections are removed.
        assertEquals(0, okHttpClient.connectionPool.connectionCount())
    }
}
