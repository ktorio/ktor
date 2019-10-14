/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests

import io.ktor.client.request.*
import io.ktor.client.response.*
import io.ktor.client.tests.utils.*
import io.ktor.http.*
import io.ktor.utils.io.core.*
import kotlin.test.*

class HeadersTest : ClientLoader() {

    @Test
    fun headersReturnNullWhenMissing(): Unit = clientTests {
        config {}
        test { client ->
            client.get<HttpResponse>("$TEST_SERVER/headers/").use {
                assertEquals(HttpStatusCode.OK, it.status)
                assertEquals("OK", it.readText())

                assertNull(it.headers["X-Nonexistent-Header"])
                assertNull(it.headers.getAll("X-Nonexistent-Header"))
            }
        }
    }
}
