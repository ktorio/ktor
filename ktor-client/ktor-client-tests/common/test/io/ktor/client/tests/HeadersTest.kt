/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.client.tests.utils.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.utils.io.*
import io.ktor.utils.io.charsets.*
import io.ktor.utils.io.core.*
import kotlin.test.*

class HeadersTest : ClientLoader() {

    @Test
    fun testHeadersReturnNullWhenMissing() = clientTests {
        test { client ->
            client.get<HttpResponse>("$TEST_SERVER/headers/").let {
                assertEquals(HttpStatusCode.OK, it.status)
                assertEquals("OK", it.readText())

                assertNull(it.headers["X-Nonexistent-Header"])
                assertNull(it.headers.getAll("X-Nonexistent-Header"))
            }
        }
    }

    @Test
    fun testHeadersMerge() = clientTests(listOf("Js")) {
        test { client ->
            client.get<HttpResponse>("$TEST_SERVER/headers-merge/") {
                accept(ContentType.Text.Html)
                accept(ContentType.Application.Json)
            }.let {
                assertEquals(HttpStatusCode.OK, it.status)
                assertEquals("JSON", it.readText())
                assertEquals("application/json; charset=UTF-8", it.headers[HttpHeaders.ContentType])
            }

            client.get<HttpResponse>("$TEST_SERVER/headers-merge/") {
                accept(ContentType.Text.Html)
                accept(ContentType.Application.Xml)
            }.let {
                assertEquals("XML", it.readText())
                assertEquals("application/xml; charset=UTF-8", it.headers[HttpHeaders.ContentType])
            }
        }
    }

    @Test
    fun testAcceptMerge() = clientTests(listOf("Js")) {
        test { client ->
            val lines = client.get<String>("$HTTP_PROXY_SERVER/headers-merge") {
                accept(ContentType.Application.Xml)
                accept(ContentType.Application.Json)
            }.split("\n")

            val acceptHeaderLine = lines.first { it.startsWith("Accept:") }
            assertEquals("Accept: application/xml,application/json", acceptHeaderLine)
        }
    }

    @Test
    fun testSingleHostHeader() = clientTests(listOf("Js", "Android")) {
        test { client ->
            client.get("$TEST_SERVER/headers/host") {
                header(HttpHeaders.Host, "CustomHost")
            }
        }
    }

    @Test
    fun testContentTypePropagation() = clientTests(skipEngines = listOf("CIO")) {
        test { client ->
            client.post<HttpResponse>("$TEST_SERVER/headers/mirror"){
                contentType(ContentType.Application.Json)
                body = "{}"
            }.let {
                assertEquals(ContentType.Application.Json.toString(), it.headers[HttpHeaders.ContentType])
            }

            client.post<HttpResponse>("$TEST_SERVER/headers/mirror"){
                contentType(ContentType.Application.OctetStream)
                body = "test".toByteArray()
            }.let {
                assertEquals(ContentType.Application.OctetStream.toString(), it.headers[HttpHeaders.ContentType])
            }

            client.post<HttpResponse>("$TEST_SERVER/headers/mirror"){
                contentType(ContentType.Text.Plain)
                body = ByteReadChannel("test")
            }.let {
                assertEquals(ContentType.Text.Plain.toString(), it.headers[HttpHeaders.ContentType])
            }
        }
    }

    @Test
    fun testDefaultContentType() = clientTests(skipEngines = listOf("CIO")) {
        test { client ->
            client.post<HttpResponse>("$TEST_SERVER/headers/mirror"){
                body = "{}"
            }.let {
                val expectedType = ContentType.Text.Plain.withCharset(Charset.forName("UTF-8"))
                assertEquals(expectedType.toString(), it.headers[HttpHeaders.ContentType])
            }

            client.post<HttpResponse>("$TEST_SERVER/headers/mirror"){
                body = "test".toByteArray()
            }.let {
                assertEquals(ContentType.Application.OctetStream.toString(), it.headers[HttpHeaders.ContentType])
            }

            client.post<HttpResponse>("$TEST_SERVER/headers/mirror"){
                body = ByteReadChannel("test")
            }.let {
                assertEquals(ContentType.Application.OctetStream.toString(), it.headers[HttpHeaders.ContentType])
            }
        }
    }

    @Test
    fun testOverrideContentType() = clientTests(skipEngines = listOf("CIO")) {
        config {
            engine {
                allowUnsafeHeaders = true
            }
        }
        test { client ->
            client.post<HttpResponse>("$TEST_SERVER/headers/mirror"){
                contentType(ContentType.Application.OctetStream)
                body = TextContent("test", ContentType.Text.Plain)
            }.let {
                assertEquals(ContentType.Text.Plain.toString(), it.headers[HttpHeaders.ContentType])
            }

            client.post<HttpResponse>("$TEST_SERVER/headers/mirror"){
                contentType(ContentType.Text.Plain)
                body = ByteArrayContent("test".toByteArray())
            }.let {
                assertEquals(ContentType.Text.Plain.toString(), it.headers[HttpHeaders.ContentType])
            }
        }
    }
}
