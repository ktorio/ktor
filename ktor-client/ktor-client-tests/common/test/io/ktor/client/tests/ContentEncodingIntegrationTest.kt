/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.client.tests.utils.*
import io.ktor.http.*
import io.ktor.util.*
import io.ktor.utils.io.*
import kotlinx.io.readString
import kotlin.test.Test
import kotlin.test.assertEquals

private const val TEST_URL = "$TEST_SERVER/compression"

class ContentEncodingIntegrationTest : ClientLoader() {

    @Test
    fun testGzipWithContentLengthWithoutPlugin() = clientTests {
        test { client ->
            val response = client.get("$TEST_URL/gzip-with-content-length")
            val byteContent = response.bodyAsBytes()

            val content = if (response.headers[HttpHeaders.ContentEncoding] == "gzip") {
                GZipEncoder.decode(ByteReadChannel(byteContent)).readRemaining().readString()
            } else {
                byteContent.decodeToString()
            }

            assertEquals("Hello, world", content)
        }
    }

    @Test
    fun testHeadGzipWithContentLengthWithoutPlugin() = clientTests {
        test { client ->
            val response = client.head("$TEST_URL/head-gzip-with-content-length")

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("gzip", response.headers[HttpHeaders.ContentEncoding], "gzip")
            assertEquals("32", response.headers[HttpHeaders.ContentLength])
        }
    }
}
