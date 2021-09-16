/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests

import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.tests.utils.*
import io.ktor.http.*
import io.ktor.util.collections.*
import io.ktor.utils.io.errors.*
import kotlinx.coroutines.*
import kotlin.test.*

class HttpRequestRetryTest {

    @Test
    fun testNoRetryByDefault() = testWithEngine(MockEngine) {
        config {
            engine {
                reuseHandlers = false
                addHandler {
                    respondError(HttpStatusCode.InternalServerError)
                }
            }
        }

        test { client ->
            assertFailsWith<ServerResponseException> {
                client.get { }
            }
        }
    }

    @Test
    fun testRetryOnNonSuccess() = testWithEngine(MockEngine) {
        config {
            engine {
                addHandler { respondError(HttpStatusCode.InternalServerError) }
                addHandler { respondError(HttpStatusCode.InternalServerError) }
                addHandler { respondError(HttpStatusCode.InternalServerError) }
                addHandler { respondOk() }
            }
            install(HttpRequestRetry) {
                retryOnServerErrors(3)
                delay { }
            }
        }

        test { client ->
            val response = client.get { }
            assertEquals(HttpStatusCode.OK, response.status)
        }
    }

    @Test
    fun testExponentialDelay() = testWithEngine(MockEngine) {
        val delays = ConcurrentList<Long>()
        config {
            engine {
                addHandler { respondError(HttpStatusCode.InternalServerError) }
                addHandler { respondError(HttpStatusCode.InternalServerError) }
                addHandler { respondError(HttpStatusCode.InternalServerError) }
                addHandler { respondOk() }
            }
            install(HttpRequestRetry) {
                retryOnServerErrors(3)
                exponentialDelay(randomizationMs = 0)
                delay { delays.add(it) }
            }
        }

        test { client ->
            client.get { }
            assertEquals(listOf(2000L, 4000L, 8000L), delays)
        }
    }

    @Test
    fun testConstantDelay() = testWithEngine(MockEngine) {
        val delays = ConcurrentList<Long>()
        config {
            engine {
                addHandler { respondError(HttpStatusCode.InternalServerError) }
                addHandler { respondError(HttpStatusCode.InternalServerError) }
                addHandler { respondError(HttpStatusCode.InternalServerError) }
                addHandler { respondOk() }
            }
            install(HttpRequestRetry) {
                retryOnServerErrors(3)
                constantDelay(millis = 123, randomizationMs = 0)
                delay { delays.add(it) }
            }
        }

        test { client ->
            client.get { }
            assertEquals(listOf(123L, 123L, 123L), delays)
        }
    }

    @Test
    fun testMaxRetries() = testWithEngine(MockEngine) {
        config {
            engine {
                addHandler { respondError(HttpStatusCode.InternalServerError) }
                addHandler { respondError(HttpStatusCode.InternalServerError) }
                addHandler { respondError(HttpStatusCode.InternalServerError) }
                addHandler { respondOk() }
            }
            install(HttpRequestRetry) {
                retryOnServerErrors(2)
                delay { }
            }
        }

        test { client ->
            assertFailsWith<ServerResponseException> {
                client.get { }
            }
        }
    }

    @Test
    fun testRetryOnException() = testWithEngine(MockEngine) {
        config {
            engine {
                addHandler { throw IOException("Network error") }
                addHandler { throw IOException("Network error") }
                addHandler { throw IOException("Network error") }
                addHandler { respondOk() }
            }
            install(HttpRequestRetry) {
                retryOnException(3)
                delay { }
            }
        }

        test { client ->
            val response = client.get { }
            assertEquals(HttpStatusCode.OK, response.status)
        }
    }

    @Test
    fun testRetryOnExceptionOrServerError() = testWithEngine(MockEngine) {
        config {
            engine {
                addHandler { throw IOException("Network error") }
                addHandler { respondError(HttpStatusCode.InternalServerError) }
                addHandler { throw IOException("Another error") }
                addHandler { respondOk() }
            }
            install(HttpRequestRetry) {
                retryOnExceptionOrServerErrors(3)
                delay { }
            }
        }

        test { client ->
            val response = client.get { }
            assertEquals(HttpStatusCode.OK, response.status)
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    @Test
    fun testRetryCanCancel() = testWithEngine(MockEngine) {
        config {
            engine {
                addHandler { throw IOException("Network error") }
                addHandler { throw IOException("Network error") }
                addHandler { throw IOException("Network error") }
                addHandler { respondOk() }
            }
            install(HttpRequestRetry) {
                retryOnExceptionOrServerErrors(3)
            }
        }

        test { client ->
            val request = HttpRequestBuilder()
            val context = request.executionContext
            GlobalScope.launch {
                context.cancel()
            }
            assertFailsWith<CancellationException> { client.get(request) }
        }
    }

    @Test
    fun testRetryPerRequestConfig() = testWithEngine(MockEngine) {
        val delays = ConcurrentList<Long>()
        config {
            engine {
                addHandler { throw IOException("Network error") }
                addHandler { respondError(HttpStatusCode.InternalServerError) }
                addHandler { throw IOException("Network error") }
                addHandler { respondError(HttpStatusCode.InternalServerError) }
                addHandler { respondOk() }
            }
            install(HttpRequestRetry) {
                noRetry()
                exponentialDelay()
                delay { delays.add(it) }
            }
        }

        test { client ->
            val response = client.get {
                retry {
                    constantDelay(millis = 123, randomizationMs = 0)
                    retryOnExceptionOrServerErrors(4)
                }
            }
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(listOf(123L, 123L, 123L, 123L), delays)
        }
    }

    @Test
    fun testRetryEvent() = testWithEngine(MockEngine) {
        config {
            engine {
                addHandler { throw IOException("Network error") }
                addHandler { respondError(HttpStatusCode.InternalServerError) }
                addHandler { respondOk() }
            }
            install(HttpRequestRetry) {
                retryOnExceptionOrServerErrors(3)
                delay { }
            }
        }

        test { client ->
            val events = ConcurrentList<HttpRequestRetry.RetryEventData>()
            client.monitor.subscribe(HttpRequestRetry.HttpRequestRetryEvent) { events.add(it) }

            client.get {}

            assertEquals(2, events.size)

            assertEquals("Network error", events[0].cause?.message)
            assertEquals(1, events[0].retryCount)
            assertEquals(null, events[0].response)

            assertEquals(null, events[1].cause)
            assertEquals(2, events[1].retryCount)
            assertEquals(HttpStatusCode.InternalServerError, events[1].response?.status)
        }
    }
}
