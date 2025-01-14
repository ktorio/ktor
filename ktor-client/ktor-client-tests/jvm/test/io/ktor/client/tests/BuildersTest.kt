/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests

import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.client.test.base.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class BuildersTest : ClientLoader() {

    @Test
    fun getEmptyResponseTest() = clientTests {
        test { client ->
            val response = client.get("$TEST_SERVER/builders/empty").body<String>()
            assertEquals("", response)
        }
    }

    @Test
    fun testNotFound() = clientTests {
        test { client ->
            assertFailsWith<ResponseException> {
                client.get("$TEST_SERVER/builders/notFound") {
                    expectSuccess = true
                }.body<String>()
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

            assertEquals("Hello, world!", client.get {}.bodyAsText())
        }
    }
}
