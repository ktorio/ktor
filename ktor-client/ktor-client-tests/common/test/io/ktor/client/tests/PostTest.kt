/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests

import io.ktor.client.*
import io.ktor.client.features.*
import io.ktor.client.request.*
import io.ktor.client.tests.utils.*
import io.ktor.http.content.*
import kotlinx.coroutines.*
import io.ktor.utils.io.*
import kotlin.test.*

class PostTest : ClientLoader() {
    @Test
    fun testPostString() = clientTests(listOf("Js")) {
        test { client ->
            client.postHelper(makeString(777))
        }
    }

    @Test
    fun testHugePost() = clientTests(listOf("Js", "iOS", "Curl")) {
        test { client ->
            client.postHelper(makeString(32 * 1024 * 1024))
        }
    }

    @Test
    fun testWithPause() = clientTests(listOf("Js", "iOS", "Curl")) {
        test { client ->
            val content = makeString(16 * 1024 * 1024)

            val response = client.post<String>("$TEST_SERVER/content/echo") {
                body = object : OutgoingContent.WriteChannelContent() {
                    override suspend fun writeTo(channel: ByteWriteChannel) {
                        channel.writeStringUtf8(content)
                        delay(1000)
                        channel.writeStringUtf8(content)
                        channel.close()
                    }
                }

            }

            assertEquals(content + content, response)
        }
    }

    private suspend fun HttpClient.postHelper(text: String) {
        val response = post<String>("$TEST_SERVER/content/echo") {
            body = text
        }
        assertEquals(text, response)
    }
}
