/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.response.*
import io.ktor.client.tests.utils.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.util.*
import kotlinx.io.core.*
import kotlin.test.*

class ContentTest : ClientLoader() {
    private val testSize = listOf(
        0, 1, // small edge cases
        4 * 1024 - 1, 4 * 1024, 4 * 1024 + 1, // ByteChannel edge cases
        16 * 1024 * 1024 // big
    )

    @Test
    fun testGetFormData() = clientTests(listOf("js")) {
        test { client ->
            val form = parametersOf(
                "user" to listOf("myuser"),
                "page" to listOf("10")
            )

            val response = client.submitForm<String>(
                "$TEST_SERVER/content/news", encodeInQuery = true, formParameters = form
            )

            assertEquals("100", response)
        }
    }

    @Test
    fun testByteArray() = clientTests(listOf("js")) {
        test { client ->
            testSize.forEach { size ->
                val content = makeArray(size)
                val response = client.echo<ByteArray>(content)

                assertArrayEquals("Test fail with size: $size", content, response)
            }
        }
    }

    @Test
    fun testByteReadChannel() = clientTests(listOf("js")) {
        test { client ->
            testSize.forEach { size ->
                val content = makeArray(size)
                client.echo<HttpResponse>(content).use { response ->
                    val responseData = response.content.toByteArray()
                    assertArrayEquals("Test fail with size: $size", content, responseData)
                }
            }
        }
    }

    @Test
    fun testString() = clientTests(listOf("js")) {
        test { client ->
            testSize.forEach { size ->
                val content = makeString(size)
                val requestWithBody = client.echo<String>(content)
                assertArrayEquals(
                    "Test fail with size: $size", content.toByteArray(), requestWithBody.toByteArray()
                )
            }
        }
    }

    @Test
    fun testTextContent() = clientTests(listOf("js")) {
        test { client ->
            testSize.forEach { size ->
                val content = makeString(size)
                val response = client.echo<String>(TextContent(content, ContentType.Text.Plain))

                assertArrayEquals("Test fail with size: $size", content.toByteArray(), response.toByteArray())
            }
        }
    }

    @Test
    fun testByteArrayContent() = clientTests(listOf("js")) {
        test { client ->
            testSize.forEach { size ->
                val content = makeArray(size)
                val response = client.echo<ByteArray>(ByteArrayContent(content))

                assertArrayEquals("Test fail with size: $size", content, response)
            }
        }
    }

    @Test
    fun testPostFormData() = clientTests(listOf("js")) {
        test { client ->
            val form = parametersOf(
                "user" to listOf("myuser"),
                "token" to listOf("abcdefg")
            )

            val response = client.submitForm<String>("$TEST_SERVER/content/sign", formParameters = form)
            assertEquals("success", response)
        }
    }

    @Test
    fun testMultipartFormData() = clientTests(listOf("js")) {
        val data = {
            formData {
                append("name", "hello")
                append("content") {
                    writeStringUtf8("123456789")
                }
                append("file", "urlencoded_name.jpg") {
                    for (i in 1..4096) {
                        writeByte(i.toByte())
                    }
                }
                append("file2", "urlencoded_name2.jpg", ContentType.Application.OctetStream) {
                    for (i in 1..4096) {
                        writeByte(i.toByte())
                    }
                }
                append("hello", 5)
            }
        }

        test { client ->
            val response = client.submitFormWithBinaryData<String>(
                "$TEST_SERVER/content/upload", formData = data()
            )
            val contentString = data().makeString()
            assertEquals(contentString, response)
        }
    }

    private suspend inline fun <reified Response : Any> HttpClient.echo(body: Any): Response =
        post("$TEST_SERVER/content/echo") {
            this.body = body
        }
}
