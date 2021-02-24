/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.tests.utils.*
import io.ktor.utils.io.*
import kotlin.test.*

class DownloadTest : ClientLoader() {
    @Test
    fun testDownloadGoogle() = clientTests {
        test { client ->
            val response = client.get("http://www.google.com/").body<String>()
            assertTrue { response.isNotEmpty() }
        }
    }

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
    fun testEchoWithChannelBody() = clientTests {
        test { client ->
            val channel = client.get("http://www.google.com/").body<ByteReadChannel>()
            val size = channel.readRemaining().remaining
            assertTrue(size > 0)
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
