/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests

import io.ktor.client.*
import io.ktor.client.features.json.*
import io.ktor.client.features.json.serializer.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.client.tests.utils.*
import io.ktor.client.utils.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.*
import kotlin.test.*

class ContentTest : ClientLoader() {
    private val testSize = listOf(
        0, 1, // small edge cases
        4 * 1024 - 1, 4 * 1024, 4 * 1024 + 1, // ByteChannel edge cases
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

            val response = client.submitForm<String>(
                "$TEST_SERVER/content/news", encodeInQuery = true, formParameters = form
            )

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
    fun testString() = clientTests(listOf("Js", "iOS", "Curl")) {
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
    fun testTextContent() = clientTests(listOf("Js", "iOS", "Curl")) {
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

            val response = client.submitForm<String>("$TEST_SERVER/content/sign", formParameters = form)
            assertEquals("success", response)
        }
    }

    @Test
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
            val response = client.submitFormWithBinaryData<String>(
                "$TEST_SERVER/content/upload", formData = data()
            )
            val contentString = data().makeString()
            assertEquals(contentString, response)
        }
    }

    @Test
    fun testFormDataWithContentLength() = clientTests(listOf("Js")) {
        test { client ->
            client.submitForm<Unit> {
                url("$TEST_SERVER/content/file-upload")
                method = HttpMethod.Put

                body = MultiPartFormDataContent(
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
            }
        }
    }

    @Test
    fun testJsonPostWithEmptyBody() = clientTests {
        config {
            install(JsonFeature) {
                serializer = KotlinxSerializer()
            }
        }

        test { client ->
            val response = client.post<String>("$TEST_SERVER/echo") {
                contentType(ContentType.Application.Json)
            }

            assertEquals("", response)
        }
    }

    @Test
    fun testPostWithEmptyBody() = clientTests {
        config {
        }

        test { client ->
            val response = client.post<String>("$TEST_SERVER/echo") {
                body = EmptyContent
            }

            assertEquals("", response)
        }
    }

    @Test
    fun testDownloadStreamChannelWithCancel() = clientTests(listOf("Js", "Curl")) {
        test { client ->
            val content = client.get<ByteReadChannel>("$TEST_SERVER/content/stream")
            content.cancel()
        }
    }

    @Test
    fun testDownloadStreamResponseWithClose() = clientTests(listOf("Js", "Curl")) {
        test { client ->
            client.get<HttpStatement>("$TEST_SERVER/content/stream").execute {
            }
        }
    }

    @Test
    fun testDownloadStreamResponseWithCancel() = clientTests(listOf("Js", "Curl")) {
        test { client ->
            client.get<HttpStatement>("$TEST_SERVER/content/stream").execute {
                it.cancel()
            }
        }
    }

    @Test
    fun testDownloadStreamArrayWithTimeout() = clientTests(listOf("Js", "Curl")) {
        test { client ->
            val result: ByteArray? = withTimeoutOrNull(100) {
                client.get<ByteArray>("$TEST_SERVER/content/stream")
            }

            assertNull(result)
        }
    }

    private suspend inline fun <reified Response : Any> HttpClient.echo(
        body: Any
    ): Response = post("$TEST_SERVER/content/echo") {
        this.body = body
    }
}
