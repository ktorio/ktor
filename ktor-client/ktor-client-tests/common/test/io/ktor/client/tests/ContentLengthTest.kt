/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests

import io.ktor.client.call.*
import io.ktor.client.plugins.compression.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.client.test.base.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

// KTOR-10111: CIO adds Content-Length: 0 to 204 responses, so use Netty listener.
private const val NETTY_TEST_SERVER = "http://127.0.0.1:8084"

class ContentLengthTest : ClientLoader(timeout = 5.seconds, retries = 0) {

    // KTOR-8377: Browser engines remove Content-Length from GET responses.
    @Test
    fun `response body matches content length`() = clientTests(except("web.browser:*")) {
        test { client ->
            val response = client.get("$NETTY_TEST_SERVER/content/hello")
            response.assertResponse(HttpStatusCode.OK, contentLength = 5L, content = "hello")
        }
    }

    @Test
    fun `response without content length is read to completion`() = clientTests {
        test { client ->
            val response = client.get("$NETTY_TEST_SERVER/content/chunked-data?size=1000")
            response.assertResponse(HttpStatusCode.OK, content = "x".repeat(1000))
        }
    }

    @Test
    fun `HEAD response ignores representation content length`() = clientTests {
        test { client ->
            val response = client.head("$NETTY_TEST_SERVER/content/no-body?length=150")
            response.assertResponse(HttpStatusCode.OK, contentLength = 150L)
        }
    }

    // KTOR-9936: JVM and Native validate representation length against the empty body.
    // KTOR-9937: OkHttp waits for the representation bytes on a keep-alive connection.
    // KTOR-8377: Browser engines remove Content-Length from GET responses.
    @Test
    fun `304 response ignores representation content length`() = clientTests(
        except("web.browser:*", "jvm:*", "native:*")
    ) {
        test { client ->
            val response = client.get("$NETTY_TEST_SERVER/content/no-body?status=304&length=150")
            response.assertResponse(HttpStatusCode.NotModified, contentLength = 150)
        }
    }

    // KTOR-9937: OkHttp waits for the representation bytes on a keep-alive connection.
    // KTOR-8377: Browser engines remove Content-Length from GET responses.
    @Test
    fun `streaming 304 response does not wait for representation bytes`() = clientTests(
        except("web.browser:*", "OkHttp")
    ) {
        test { client ->
            client.prepareGet("$NETTY_TEST_SERVER/content/no-body?status=304&length=150").execute { response ->
                response.assertResponse(HttpStatusCode.NotModified, contentLength = 150, content = null)
                assertContentEquals(byteArrayOf(), response.bodyAsChannel().toByteArray())
            }
        }
    }

    // KTOR-10111: Netty with HTTP/2 sends Content-Length: 0 for 204, and Java engine exposes this header.
    @Test
    fun `204 response has no content length or body`() = clientTests(except("Java")) {
        test { client ->
            val response = client.get("$NETTY_TEST_SERVER/content/no-body?status=204")
            response.assertResponse(HttpStatusCode.NoContent)
        }
    }

    @Test
    fun `HEAD response preserves representation length and encoding`() = clientTests {
        test { client ->
            val response = client.head("$NETTY_TEST_SERVER/compression/head-gzip-with-content-length")
            response.assertResponse(HttpStatusCode.OK, contentLength = 32L, encoding = "gzip")
        }
    }

    // KTOR-9934: JVM ContentEncoding tries to decode the empty body as gzip.
    @Test
    fun `HEAD response with content encoding does not decode a body`() = clientTests(except("jvm:*")) {
        config {
            ContentEncoding { gzip() }
        }
        test { client ->
            val response = client.head("$NETTY_TEST_SERVER/compression/head-gzip-with-content-length")
            response.assertResponse(HttpStatusCode.OK, contentLength = 32L, encoding = "gzip")
        }
    }

    // KTOR-9934: JVM ContentEncoding tries to decode the empty body as gzip.
    // KTOR-9936: Native CIO validates representation length against the empty body.
    // KTOR-10116: Native and web engines strip headers from bodiless responses.
    @Test
    fun `304 response with content encoding does not decode a body`() = clientTests(
        except("jvm:*", "native:*", "web:*")
    ) {
        config {
            ContentEncoding { gzip() }
        }
        test { client ->
            val response = client.get("$NETTY_TEST_SERVER/content/no-body?status=304&length=150&encoding=gzip")
            response.assertResponse(HttpStatusCode.NotModified, contentLength = 150, encoding = "gzip")
        }
    }

    // KTOR-9934: JVM ContentEncoding tries to decode the empty body as gzip.
    // KTOR-10116: Native and web engines strip headers from bodiless responses.
    @Test
    fun `204 response with content encoding does not decode a body`() = clientTests(
        except("jvm:*", "native:*", "web:*")
    ) {
        config {
            ContentEncoding { gzip() }
        }
        test { client ->
            val response = client.get("$NETTY_TEST_SERVER/content/no-body?status=204&encoding=gzip")
            response.assertResponse(HttpStatusCode.NoContent, encoding = "gzip")
        }
    }
}

private suspend fun HttpResponse.assertResponse(
    status: HttpStatusCode,
    contentLength: Long? = null,
    encoding: String? = null,
    content: String? = "",
) {
    assertEquals(status, this.status, "Unexpected status code")
    assertEquals(contentLength, this.headers[HttpHeaders.ContentLength]?.toLong(), "Unexpected content length")
    assertEquals(encoding, this.headers[HttpHeaders.ContentEncoding], "Unexpected content encoding")
    if (content != null) assertContentEquals(content.encodeToByteArray(), body<ByteArray>(), "Unexpected body content")
}
