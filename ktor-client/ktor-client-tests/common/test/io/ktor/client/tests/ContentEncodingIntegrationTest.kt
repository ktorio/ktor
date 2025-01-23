/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.client.test.base.*
import io.ktor.http.*
import io.ktor.util.*
import io.ktor.utils.io.*
import kotlinx.io.readString
import kotlin.test.Test
import kotlin.test.assertEquals

private const val TEST_URL = "$TEST_SERVER/compression"

class ContentEncodingIntegrationTest : ClientLoader() {

    // GZipEncoder is implemented only on JVM.
    @Test
    fun testGzipWithContentLengthWithoutPlugin() = clientTests(only("jvm:*", "web:Js")) {
        test { client ->
            val response = client.get("$TEST_URL/gzip-with-content-length")
            val content = if (response.headers[HttpHeaders.ContentEncoding] == "gzip") {
                GZipEncoder.decode(response.bodyAsChannel()).readRemaining().readString()
            } else {
                response.bodyAsText()
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
