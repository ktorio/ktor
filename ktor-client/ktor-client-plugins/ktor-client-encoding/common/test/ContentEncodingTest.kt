/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.plugins.compression

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.test.base.*
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
            // JS browser engines don't have Content-Encoding header for identity encoding
            if (response.appliedDecoders.isNotEmpty()) {
                assertEquals(listOf("identity"), response.appliedDecoders)
            }
            assertEquals("Compressed response!", response.body<String>())
        }
    }

    // Note: JS is decompressed before hitting the client, so the response does not appear compressed
    @Test
    fun testDeflate() = clientTests(except("native:CIO", "Js")) {
        config {
            ContentEncoding {
                deflate()
            }
        }

        test { client ->
            val response = client.get("$TEST_URL/deflate")
            assertNull(response.headers[HttpHeaders.ContentEncoding])
            assertEquals(listOf("deflate"), response.appliedDecoders)
            assertEquals("Compressed response!", response.body<String>())
        }
    }

    @Test
    fun testGZip() = clientTests(except("native:CIO", "Js")) {
        config {
            ContentEncoding {
                gzip()
            }
        }

        test { client ->
            val response = client.get("$TEST_URL/gzip")
            assertNull(response.headers[HttpHeaders.ContentEncoding])
            assertEquals(listOf("gzip"), response.appliedDecoders)
            assertEquals("Compressed response!", response.body<String>())
        }
    }

    @Test
    fun testGZipEmpty() = clientTests(except("Js")) {
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
    fun testGzipByteArray() = clientTests(except("Js")) {
        config {
            ContentEncoding {
                gzip()
            }
        }

        test { client ->
            val response = client.get("$TEST_URL/gzip-precompressed")
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(listOf("gzip"), response.appliedDecoders)
            val body = response.body<ByteArray>()
            assertContentEquals(ByteArray(500) { it.toByte() }, body)
        }
    }

    @Test
    fun testDisableDecompression() = clientTests(except("OkHttp", "Js")) {
        config {
            ContentEncoding(mode = ContentEncodingConfig.Mode.CompressRequest) {
                gzip()
            }
        }

        test { client ->
            val response = client.get("$TEST_URL/gzip-precompressed")
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("gzip", response.headers[HttpHeaders.ContentEncoding])
        }
    }

    @Test
    fun testNoEncoding() = clientTests(except("OkHttp")) {
        config {
            install(ContentEncoding)
        }

        test { client ->
            val response = client.get("$TEST_URL/big-plain-text")
            assertEquals(HttpStatusCode.OK, response.status)
        }
    }

    // KTOR-9934: JVM ContentEncoding tries to decode the empty body as gzip.
    // KTOR-10116: Non-Darwin Native strips representation headers from bodiless responses.
    @Test
    fun `HEAD response with content encoding does not decode a body`() = clientTests(
        except("jvm:*", "native/linux:*", "native/windows:*")
    ) {
        config {
            ContentEncoding { gzip() }
        }
        test { client ->
            val response = client.head("$TEST_URL/head-gzip-with-content-length")
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("32", response.headers[HttpHeaders.ContentLength])
            assertEquals("gzip", response.headers[HttpHeaders.ContentEncoding])
            assertEquals(emptyList(), response.appliedDecoders)
            assertContentEquals(byteArrayOf(), response.body<ByteArray>())
        }
    }

    // KTOR-9934: JVM ContentEncoding tries to decode the empty body as gzip.
    // KTOR-9936: Native CIO validates representation length against the empty body.
    // KTOR-10116: Native and web engines strip headers from bodiless responses.
    @Ignore
    @Test
    fun `304 response with content encoding does not decode a body`() = clientTests(
        except("jvm:*", "native:*", "web:*")
    ) {
        config {
            ContentEncoding { gzip() }
        }
        test { client ->
            val response = client.get("$TEST_SERVER/content/no-body?status=304&length=150&encoding=gzip")
            assertEquals(HttpStatusCode.NotModified, response.status)
            assertEquals("150", response.headers[HttpHeaders.ContentLength])
            assertEquals("gzip", response.headers[HttpHeaders.ContentEncoding])
            assertEquals(emptyList(), response.appliedDecoders)
            assertContentEquals(byteArrayOf(), response.body<ByteArray>())
        }
    }

    // KTOR-9934: JVM ContentEncoding tries to decode the empty body as gzip.
    // KTOR-10116: Native and web engines strip headers from bodiless responses.
    @Ignore
    @Test
    fun `204 response with content encoding does not decode a body`() = clientTests(
        except("jvm:*", "native:*", "web:*")
    ) {
        config {
            ContentEncoding { gzip() }
        }
        test { client ->
            val response = client.get("$TEST_SERVER/content/no-body?status=204&encoding=gzip")
            assertEquals(HttpStatusCode.NoContent, response.status)
            assertNull(response.headers[HttpHeaders.ContentLength])
            assertEquals("gzip", response.headers[HttpHeaders.ContentEncoding])
            assertEquals(emptyList(), response.appliedDecoders)
            assertContentEquals(byteArrayOf(), response.body<ByteArray>())
        }
    }
}
