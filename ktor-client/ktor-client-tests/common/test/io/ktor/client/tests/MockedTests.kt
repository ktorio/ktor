/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests

import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import io.ktor.client.tests.utils.*
import io.ktor.http.*
import kotlin.test.*

class MockedTests {
    @Test
    fun testPostWithStringResult() = clientTest(MockEngine) {
        config {
            engine {
                addHandler {
                    respondOk("content")
                }
            }
        }
        test { client ->
            val url = "http://localhost"
            val accessToken = "Hello"
            val text = "{}"
            val response: String = client.post {
                url(url)
                body = text
                headers {
                    append("Authorization", "Bearer $accessToken")
                    append(HttpHeaders.ContentType, "application/json")
                }
            }
        }
    }
}
