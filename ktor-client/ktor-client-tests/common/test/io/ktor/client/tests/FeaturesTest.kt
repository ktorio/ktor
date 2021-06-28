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
import kotlinx.coroutines.*
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
    @Ignore
    fun testIgnoreBodyWithoutPipelining() = clientTests {
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
    fun testBodyObserver() = clientTests(listOf("CIO", "Curl")) {
        val body = "Hello, world"
        val task = Job()
        config {
            ResponseObserver { response ->
                val text = response.body<String>()
                assertEquals(body, text)
                task.complete()
            }
        }

        test { client ->
            client.prepareGet("$TEST_SERVER/features/echo").execute {
                val text = it.body<String>()
                assertEquals(body, text)
            }

            task.join()
        }
    }

    private suspend fun HttpClient.getIgnoringBody(size: Int) {
        get("$TEST_SERVER/features/body") {
            parameter("size", size.toString())
        }.body<Unit>()
    }
}
