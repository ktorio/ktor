/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.features.observer.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.client.tests.utils.*
import kotlin.test.*

class FeaturesTest : ClientLoader() {
    private val testSize = listOf(0, 1, 1024, 4 * 1024, 16 * 1024, 16 * 1024 * 1024)

    @Test
    fun testIgnoreBody() = clientTests {
        test { client ->
            testSize.forEach {
                client.getIgnoringBody(it)
            }
        }
    }

    @Test
    fun testIgnoreBodyWithoutPipelining() = clientTest {
        config {
            engine {
                pipelining = false
            }
        }

        test { client ->
            testSize.forEach {
                client.getIgnoringBody(it)
            }
        }
    }

    @Test
    fun bodyObserverTest() {
        var observerExecuted = false
        clientTests {
            val body = "Hello, world"
            config {
                ResponseObserver { response ->
                    val text = response.receive<String>()
                    assertEquals(body, text)
                    observerExecuted = true
                }
            }

            test { client ->
                client.get<HttpStatement>("$TEST_SERVER/features/echo").execute {
                    val text = it.receive<String>()
                    assertEquals(body, text)
                }
            }
        }

        assertTrue(observerExecuted)
    }

    suspend fun HttpClient.getIgnoringBody(size: Int) {
        get<Unit>("$TEST_SERVER/features/body") {
            parameter("size", size.toString())
        }
    }
}
