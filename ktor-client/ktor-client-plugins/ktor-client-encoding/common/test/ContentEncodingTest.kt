/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.plugins.compression

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.tests.utils.*
import io.ktor.http.*
import kotlin.test.*

private const val TEST_URL = "$TEST_SERVER/compression"

class ContentEncodingTest : ClientLoader() {
    @Test
    fun testIdentity() = clientTests {
        config {
            ContentEncoding {
                identity()
            }
        }

        test { client ->
            val response = client.get("$TEST_URL/identity")
            val contentEncoding = response.headers[HttpHeaders.ContentEncoding]
            // JS browser engines don't have Content-Encoding header for identity encoding
            if (contentEncoding != null) {
                assertEquals("identity", contentEncoding)
            }
            assertEquals("Compressed response!", response.body<String>())
        }
    }

    @Test
    fun testDeflate() = clientTests(listOf("native:CIO")) {
        config {
            ContentEncoding {
                deflate()
            }
        }

        test { client ->
            val response = client.get("$TEST_URL/deflate")
            assertEquals("deflate", response.headers[HttpHeaders.ContentEncoding])
            assertEquals("Compressed response!", response.body<String>())
        }
    }

    @Test
    fun testGZip() = clientTests(listOf("native:CIO")) {
        config {
            ContentEncoding {
                gzip()
            }
        }

        test { client ->
            val response = client.get("$TEST_URL/gzip")
            assertEquals("gzip", response.headers[HttpHeaders.ContentEncoding])
            assertEquals("Compressed response!", response.body<String>())
        }
    }

    @Test
    fun testGZipEmpty() = clientTests {
        config {
            ContentEncoding {
                gzip()
            }
        }

        test { client ->
            val response = client.get("$TEST_URL/gzip-empty")
            assertEquals("gzip", response.headers[HttpHeaders.ContentEncoding])
            assertEquals("", response.body())
        }
    }

    @Test
    fun testGzipByteArray() = clientTests {
        config {
            ContentEncoding {
                gzip()
            }
        }

        test { client ->
            val response = client.get("$TEST_URL/gzip-precompressed")
            assertEquals(HttpStatusCode.OK, response.status)
            val body = response.body<ByteArray>()
            assertContentEquals(ByteArray(500) { it.toByte() }, body)
        }
    }
}
