/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.test.base.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.utils.io.*
import kotlinx.io.readByteArray
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Tests client request with multi-part form data.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.tests.MultiPartFormDataTest)
 */
class MultiPartFormDataTest : ClientLoader() {

    private val fileDispositionHeaders = Headers.build {
        append(
            HttpHeaders.ContentDisposition,
            """form-data; name="file"; filename="test.png""""
        )
    }

    @Test
    fun testMultiPartFormData() = clientTests(except("native:*")) {
        test { client ->
            val result = client.preparePost("$TEST_SERVER/multipart") {
                setBody(
                    MultiPartFormDataContent(
                        formData {
                            append("file", ByteArray(1024 * 1024), fileDispositionHeaders)
                        }
                    )
                )
            }.execute()

            assertEquals(HttpStatusCode.OK, result.status)
        }
    }

    @Test
    fun testEmptyMultiPartFormData() = clientTests {
        test { client ->
            val response = client.submitFormWithBinaryData("$TEST_SERVER/multipart/empty", emptyList())
            assertTrue(response.status.isSuccess())
        }
    }

    @Test
    fun testReceiveMultiPartFormData() = clientTests {
        test { client ->
            val response = client.post("$TEST_SERVER/multipart/receive")

            val multipart = response.body<MultiPartData>()
            var textFound = false
            var fileFound = false

            multipart.forEachPart { part ->
                when (part) {
                    is PartData.FormItem -> {
                        assertEquals("text", part.name)
                        assertEquals("Hello, World!", part.value)
                        textFound = true
                    }
                    is PartData.FileItem -> {
                        assertEquals("file", part.name)
                        assertEquals("test.bin", part.originalFileName)

                        val bytes = part.provider().readRemaining().readByteArray()
                        assertEquals(1024, bytes.size)
                        for (i in bytes.indices) {
                            assertEquals(i.toByte(), bytes[i])
                        }
                        fileFound = true
                    }
                    else -> fail("Unexpected part type: ${part::class.simpleName}")
                }
                part.dispose()
            }

            assertTrue(textFound, "Text part not found")
            assertTrue(fileFound, "File part not found")
        }
    }
}
