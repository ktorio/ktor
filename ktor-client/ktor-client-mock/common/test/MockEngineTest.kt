/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.mock

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class MockEngineTest {

    @Test
    fun testHistoryIteration() {
        val client = HttpClient(MockEngine) {
            engine {
                addHandler {
                    respond("Test")
                }
            }
        }
        val engine = client.engine as MockEngine
        val count = engine.requestHistory
            .map(HttpRequestData::url)
            .map(Url::toString)
            .count()

        assertEquals(0, count)
    }

    @Test
    fun testRejectsRequestWithMalformedUrl() = runTest {
        val client = HttpClient(MockEngine) {
            defaultRequest {
                url("http://localhost") {
                    appendPathSegments("numbers/")
                }
            }
            engine {
                addHandler { respond("OK") }
            }
        }

        val failure = assertFailsWith<IllegalArgumentException> { client.get("123") }
        assertTrue(failure.message.orEmpty().contains("scheme"))
    }

    @Test
    fun testRelativeUrlIsAccepted() = runTest {
        val client = HttpClient(MockEngine) {
            engine {
                addHandler { respond("OK") }
            }
        }

        assertEquals("OK", client.get("123").body<String>())
    }
}
