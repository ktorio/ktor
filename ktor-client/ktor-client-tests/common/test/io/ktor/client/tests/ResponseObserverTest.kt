/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests

import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.observer.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.client.test.base.*
import kotlin.test.*

class ResponseObserverTest : ClientLoader() {
    private var observerCalled = false

    @Test
    fun testEmptyResponseObserverIsNotFreezing() = clientTests {
        config {
            ResponseObserver {
            }
        }

        test { client ->
            client.get("$TEST_SERVER/download") {
                parameter("size", (1024 * 10).toString())
            }
        }
    }

    @Test
    fun testThrowInResponseObserverIsNotFailingRequest() = clientTests {
        config {
            ResponseObserver {
                error("fail")
            }
        }

        test { client ->
            client.get("$TEST_SERVER/download") {
                parameter("size", (1024 * 10).toString())
            }
        }
    }

    @Test
    fun testResponseObserverCalledWhenNoFilterPresent() = clientTests {
        config {
            install(ResponseObserver) {
                onResponse { observerCalled = true }
            }
        }

        test { client ->
            client.get("$TEST_SERVER/download") {
                parameter("size", (1024 * 10).toString())
            }
            assertTrue { observerCalled }
        }
    }

    @Test
    fun testResponseObserverCalledWhenFilterMatched() = clientTests {
        config {
            install(ResponseObserver) {
                onResponse { observerCalled = true }
                filter { true }
            }
        }

        test { client ->
            client.get("$TEST_SERVER/download") {
                parameter("size", (1024 * 10).toString())
            }
            assertTrue { observerCalled }
        }
    }

    @Test
    fun testResponseObserverNotCalledWhenFilterNotMatched() = clientTests {
        config {
            install(ResponseObserver) {
                onResponse { observerCalled = true }
                filter { false }
            }
        }

        test { client ->
            client.get("$TEST_SERVER/download") {
                parameter("size", (1024 * 10).toString())
            }
            assertFalse { observerCalled }
        }
    }

    @Test
    fun testSavedResponseCanBeReadMultipleTimes() = testWithEngine(MockEngine) {
        val bodyContent = "Hello"
        var wasSaved: Boolean? = null

        suspend fun assertBodyCanBeReadMultipleTimes(response: HttpResponse) {
            assertEquals(bodyContent, response.bodyAsText(), "First read failed")
            assertEquals(bodyContent, response.bodyAsText(), "It should be possible to read body multiple times")
        }

        config {
            install(ResponseObserver) {
                onResponse { response ->
                    wasSaved = response.isSaved
                    // In the response observer itself
                    assertBodyCanBeReadMultipleTimes(response)
                }
            }

            engine {
                addHandler { respondOk(bodyContent) }
            }
        }

        test { client ->
            // In the pipeline after ResponseObserver
            client.receivePipeline.intercept(HttpReceivePipeline.After) { response ->
                assertBodyCanBeReadMultipleTimes(response)
                proceedWith(response)
            }

            val response = client.get("/")
            assertNotNull(wasSaved, "Response observer should be called")
            assertTrue(wasSaved!!, "Response should be saved before reaching the response observer")

            // After all pipelines
            assertBodyCanBeReadMultipleTimes(response)
        }
    }
}
