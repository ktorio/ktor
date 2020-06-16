/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests

import io.ktor.client.features.*
import io.ktor.client.request.*
import io.ktor.client.tests.utils.*
import kotlin.test.*

class BuildersTest : ClientLoader() {

    @Test
    fun getEmptyResponseTest() = clientTests {
        test { client ->
            val response = client.get<String>("$TEST_SERVER/builders/empty")
            assertEquals("", response)
        }
    }

    @Test
    fun testNotFound() = clientTests {
        test { client ->
            assertFailsWith<ResponseException> {
                client.get<String>("$TEST_SERVER/builders/notFound")
            }
        }
    }

    @Test
    fun testDefaultRequest() = clientTests {
        test { rawClient ->

            val client = rawClient.config {
                defaultRequest {
                    host = "127.0.0.1"
                    port = 8080
                }
            }

            assertEquals("hello", client.get<String>(path = "builders/hello"))
        }
    }
}
