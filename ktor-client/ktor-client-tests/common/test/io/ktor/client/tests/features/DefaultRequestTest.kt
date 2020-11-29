/*
 * Copyright 2014-2020 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests.features

import io.ktor.client.features.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.client.tests.utils.*
import kotlin.test.*

class DefaultRequestTest: ClientLoader() {
    @Test
    fun testBaseURLConfig() = clientTests {
        config {
            defaultRequest {
                baseURL("https://ktor.io")
                header("something", "42")
            }
        }

        test { client ->
            with(client.get<HttpResponse>("/docs").call.request) {
                assertEquals("https://ktor.io/docs/", url.toString())
                assertEquals("42", headers["something"])
            }
            val overriddenURL = client.get<HttpResponse>("https://jetbrains.com/kotlin").call.request.url.toString()
            assertEquals("https://kotlinlang.org/", overriddenURL)
        }
    }

    @Test
    fun testBaseURLConfigWithParameter() = clientTests {
        config {
            defaultRequest {
                assertFailsWith<IllegalArgumentException> {
                    baseURL("https://ktor.io?a")
                }
            }
        }
    }
}
