/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.client.test.base.*
import io.ktor.http.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConnectionTest : ClientLoader() {
    private val testContent = buildString {
        append("x".repeat(100))
    }

    @Test
    fun testContentLengthWithEmptyBody() = clientTests {
        test { client ->
            repeat(10) {
                val response = client.request {
                    method = HttpMethod.Head
                    url.takeFrom("$TEST_SERVER/content/emptyHead")
                }

                response.also {
                    assertTrue(it.status.isSuccess())
                    assertTrue(it.readRawBytes().isEmpty())
                }
            }
        }
    }

    @Test
    fun testCloseResponseWithConnectionPipeline() = clientTests {
        suspend fun HttpClient.testCall(): HttpResponse = request {
            url.takeFrom("$TEST_SERVER/content/xxx")
        }

        test { client ->
            client.testCall()
            assertEquals(testContent, client.testCall().body())
        }
    }
}
