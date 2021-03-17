/*
 * Copyright 2014-2020 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests.plugins

import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.tests.utils.*
import kotlin.test.*

class DefaultRequestTest : ClientLoader() {
    @Test
    fun testBaseURLConfig() = clientTests {
        config {
            defaultRequest {
                header("foo", "bar")
                baseURL(TEST_SERVER)
                header("something", "42")
            }
        }

        test { client ->
            with(client.post("/echo").call.request) {
                assertEquals("$TEST_SERVER/echo", url.toString())
                assertEquals("42", headers["something"])
                assertEquals("bar", headers["foo"])
            }
        }
    }
}
