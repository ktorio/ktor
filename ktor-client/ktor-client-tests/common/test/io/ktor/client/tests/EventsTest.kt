/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests

import io.ktor.client.plugins.*
import io.ktor.client.plugins.cache.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.client.test.base.*
import io.ktor.client.utils.*
import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class EventsTest : ClientLoader() {
    private val created = atomic(0)
    private val ready = atomic(0)
    private val received = atomic(0)
    private val counter = atomic(0)
    private val cause: AtomicRef<Throwable?> = atomic(null)

    @Test
    fun testBasicEvents() = clientTests {
        test { client ->
            created.value = 0
            ready.value = 0
            received.value = 0

            client.monitor.subscribe(HttpRequestCreated) {
                created.incrementAndGet()
            }

            client.monitor.subscribe(HttpRequestIsReadyForSending) {
                ready.incrementAndGet()
            }

            client.monitor.subscribe(HttpResponseReceived) {
                received.incrementAndGet()
            }

            client.get("$TEST_SERVER/events/basic")

            assertEquals(1, created.value)
            assertEquals(1, ready.value)
            assertEquals(1, received.value)
        }
    }

    @Test
    fun testFailed() = clientTests {
        test { client ->
            cause.value = null
            counter.value = 0

            client.monitor.subscribe(HttpResponseReceiveFailed) {
                cause.value = it.cause
                counter.incrementAndGet()
            }

            client.responsePipeline.intercept(HttpResponsePipeline.Parse) {
                error("my-cause")
            }

            try {
                client.get("$TEST_SERVER/events/basic").bodyAsText()
            } catch (_: Throwable) {
            }

            assertEquals(1, counter.value)
            assertNotNull(cause.value)
            assertEquals("my-cause", cause.value?.message)
        }
    }

    @Test
    fun testRedirectEvent() = clientTests(except("Js")) {
        test { client ->
            counter.value = 0
            client.monitor.subscribe(HttpResponseRedirectEvent) {
                counter.incrementAndGet()
            }

            client.get("$TEST_SERVER/events/redirect")
            assertEquals(1, counter.value)
        }
    }

    @Test
    fun testCacheEvent() = clientTests {
        config {
            install(HttpCache)
        }

        test { client ->
            counter.value = 0
            client.monitor.subscribe(HttpCache.HttpResponseFromCache) {
                counter.incrementAndGet()
            }

            client.get("$TEST_SERVER/events/cache")
            client.get("$TEST_SERVER/events/cache")
            assertEquals(1, counter.value)
        }
    }
}
