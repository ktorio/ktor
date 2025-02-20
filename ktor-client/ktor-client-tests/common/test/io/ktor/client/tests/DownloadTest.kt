/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.test.base.*
import kotlin.test.Test
import kotlin.test.assertEquals

class DownloadTest : ClientLoader() {
    @Test
    fun testLocalhostEcho() = clientTests {
        val text = "Hello, world"
        test { client ->
            val response = client.post("$TEST_SERVER/echo") {
                setBody(text)
            }.body<String>()

            assertEquals(text, response)
        }
    }

    @Test
    fun testDownload8175() = clientTests {
        test { client ->
            repeat(100) {
                val url = "$TEST_SERVER/download/8175"
                client.get(url).body<String>()
            }
        }
    }
}
