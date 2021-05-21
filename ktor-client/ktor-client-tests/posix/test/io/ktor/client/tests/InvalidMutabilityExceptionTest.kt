/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.client.tests.utils.*
import kotlinx.coroutines.*
import kotlin.test.*

class InvalidMutabilityExceptionTest : ClientLoader() {

    @Test
    fun testSavedCall() = clientTests() {
        test { client ->
            val cause = assertFails {
                val response = withContext(Dispatchers.Default) {
                    client.get("$TEST_SERVER/content/hello")
                }
                response.bodyAsText()
            }

            assertIs<IllegalStateException>(cause)
            assertEquals(
                "Failed to receive call(HttpClientCall[http://127.0.0.1:8080/content/hello, 200 OK]) " +
                    "in different native thread.",
                cause.message
            )
        }
    }
}
