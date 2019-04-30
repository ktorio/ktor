/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests

import io.ktor.client.request.*
import io.ktor.client.tests.utils.*
import kotlin.test.*


class DownloadTest {
    @Test
    fun testDownloadGoogle() = clientsTest {
        test { client ->
            val response = client.get<String>("http://www.google.com/")
            assertTrue { response.isNotEmpty() }
        }
    }

    @Test
    fun testLocalhostEcho() = clientsTest {
        val text = "Hello, world"
        test { client ->
            val response = client.post<String>("$TEST_SERVER/echo") {
                body = text
            }

            assertEquals(text, response)
        }
    }
}
