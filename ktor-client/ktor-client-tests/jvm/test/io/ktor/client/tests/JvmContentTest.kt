/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.tests.utils.*
import org.junit.*
import java.io.*

class JvmContentTest : ClientLoader() {
    private val testSize = listOf(
        0, 1, // small edge cases
        4 * 1024 - 1, 4 * 1024, 4 * 1024 + 1, // ByteChannel edge cases
        16 * 1024 * 1024 // big
    )

    @Test
    fun inputStreamTest() = clientTests {
        testSize.forEach { size ->
            val content = makeArray(size)

            test { client ->
                val responseData = client.echo<InputStream>(content).use { response ->
                    response.readBytes()
                }

                assertArrayEquals("Test fail with size: $size", content, responseData)
            }
        }
    }

    private suspend inline fun <reified Response : Any> HttpClient.echo(body: Any): Response =
        post("$TEST_SERVER/content/echo") {
            this.body = body
        }
}
