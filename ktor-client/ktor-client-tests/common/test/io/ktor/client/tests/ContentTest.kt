/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.features.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.client.tests.utils.*
import io.ktor.client.utils.*
import io.ktor.common.serialization.*
import io.ktor.common.serialization.kotlinx.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.*
import kotlin.test.*

class ContentTest : ClientLoader() {
    private val testSize = listOf(
        0,
        1, // small edge cases
        4 * 1024 - 1,
        4 * 1024,
        4 * 1024 + 1, // ByteChannel edge cases
        10 * 4 * 1024, // 4 chunks
        10 * 4 * (1024 + 8), // 4 chunks
        8 * 1024 * 1024 // big
    )

    @Test
    fun testGetFormData() = clientTests(listOf("Js")) {
        test { client ->
            val form = parametersOf(
                "user" to listOf("myuser"),
                "page" to listOf("10")
            )

            val response = client.submitForm(
                "$TEST_SERVER/content/news",
                encodeInQuery = true,
                formParameters = form
            ).body<String>()

            assertEquals("100", response)
        }
    }

    @Test
    fun testByteArray() = clientTests(listOf("Js")) {
        test { client ->
            testSize.forEach { size ->
                val content = makeArray(size)
                val response = client.echo<ByteArray>(content)

                assertArrayEquals("Test fail with size: $size. Actual size: ${response.size}", content, response)
            }
        }
    }

    @Test
    fun testByteReadChannel() = clientTests(listOf("Js")) {
        test { client ->
            testSize.forEach { size ->
                val content = makeArray(size)
                val responseData = client.echo<ByteReadChannel>(content)
                val data = responseData.readRemaining().readBytes()
                assertArrayEquals(
                    "Test fail with size: $size, actual size: ${data.size}",
                    content,
                    data
                )
            }
        }
    }

    @Test
    fun testString() = clientTests(listOf("Js", "iOS", "Curl", "CIO")) {
        test { client ->
            testSize.forEach { size ->
                val content = makeString(size)
                val requestWithBody = client.echo<String>(content)
                assertArrayEquals(
                    "Test fail with size: $size",
                    content.toByteArray(),
                    requestWithBody.toByteArray()
                )
            }
        }
    }

    @Test
    fun testEmptyContent() = clientTests(listOf("js")) {
        val size = 0
        val content = makeString(size)
        repeatCount = 200
        test { client ->
            val response = client.echo<String>(TextContent(content, ContentType.Text.Plain))

            assertArrayEquals(
                "Test fail with size: $size",
                content.toByteArray(),
                response.toByteArray()
            )
        }
    }

    @Test
    fun testTextContent() = clientTests(listOf("Js", "iOS", "Curl", "CIO")) {
        test { client ->
            testSize.forEach { size ->
                val content = makeString(size)
                val response = client.echo<String>(TextContent(content, ContentType.Text.Plain))

                assertArrayEquals("Test fail with size: $size", content.toByteArray(), response.toByteArray())
            }
        }
    }

    @Test
    fun testByteArrayContent() = clientTests(listOf("Js")) {
        test { client ->
            testSize.forEach { size ->
                val content = makeArray(size)
                val response = client.echo<ByteArray>(ByteArrayContent(content))

                assertArrayEquals("Test fail with size: $size", content, response)
            }
        }
    }

    @Test
    fun testPostFormData() = clientTests(listOf("Js")) {
        test { client ->
            val form = parametersOf(
                "user" to listOf("myuser"),
                "token" to listOf("abcdefg")
            )

            val response = client.submitForm("$TEST_SERVER/content/sign", formParameters = form).body<String>()
            assertEquals("success", response)
        }
    }

    @Test
    @Ignore
    fun testMultipartFormData() = clientTests(listOf("Js")) {
        val data = {
            formData {
                append("name", "hello")
                append("content") {
                    writeText("123456789")
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
            val response = client.submitFormWithBinaryData(
                "$TEST_SERVER/content/upload",
                formData = data()
            ).body<String>()
            val contentString = data().makeString()
            assertEquals(contentString, response)
        }
    }

    @Test
    fun testFormDataWithContentLength() = clientTests(listOf("Js")) {
        test { client ->
            client.submitForm {
                url("$TEST_SERVER/content/file-upload")
                method = HttpMethod.Put

                setBody(
                    MultiPartFormDataContent(
                        formData {
                            appendInput(
                                "image",
                                Headers.build {
                                    append(HttpHeaders.ContentType, "image/jpg")
                                    append(HttpHeaders.ContentDisposition, "filename=hello.jpg")
                                },
                                size = 4
                            ) { buildPacket { writeInt(42) } }
                        }
                    )
                )
            }.body<Unit>()
        }
    }

    @Test
    fun testJsonPostWithEmptyBody() = clientTests {
        config {
            install(ContentNegotiation) { json() }
        }

        test { client ->
            val response = client.post("$TEST_SERVER/echo") {
                contentType(ContentType.Application.Json)
            }.body<String>()

            assertEquals("", response)
        }
    }

    @Test
    fun testPostWithEmptyBody() = clientTests {
        config {
        }

        test { client ->
            val response = client.post("$TEST_SERVER/echo") {
                setBody(EmptyContent)
            }.body<String>()

            assertEquals("", response)
        }
    }

    @Test
    @Ignore
    fun testDownloadStreamChannelWithCancel() = clientTests(listOf("Js", "Curl")) {
        test { client ->
            val content = client.get("$TEST_SERVER/content/stream").body<ByteReadChannel>()
            content.cancel()
        }
    }

    @Test
    fun testDownloadStreamResponseWithClose() = clientTests(listOf("Js", "Curl", "CIO")) {
        test { client ->
            client.prepareGet("$TEST_SERVER/content/stream").execute {
            }
        }
    }

    @Test
    fun testDownloadStreamResponseWithCancel() = clientTests(listOf("Js", "Curl")) {
        test { client ->
            client.prepareGet("$TEST_SERVER/content/stream").execute {
                it.cancel()
            }
        }
    }

    @Test
    fun testDownloadStreamArrayWithTimeout() = clientTests(listOf("Js", "Curl", "CIO")) {
        test { client ->
            val result: ByteArray? = withTimeoutOrNull(100) {
                client.get("$TEST_SERVER/content/stream").body<ByteArray>()
            }

            assertNull(result)
        }
    }

    private suspend inline fun <reified Response : Any> HttpClient.echo(
        body: Any
    ): Response = post("$TEST_SERVER/content/echo") {
        this.body = body
    }.body()
}
