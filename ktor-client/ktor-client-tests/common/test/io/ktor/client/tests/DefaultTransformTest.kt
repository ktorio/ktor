/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import io.ktor.client.tests.utils.*
import io.ktor.http.*
import io.ktor.test.*
import kotlinx.io.Buffer
import kotlinx.io.writeString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DefaultTransformTest {

    @Test
    fun testReadingHeadResponseAsByteArray() = runTest {
        val client = HttpClient(MockEngine) {
            engine {
                addHandler { _ ->
                    respond("", headers = headersOf(HttpHeaders.ContentLength, "123"))
                }
            }
        }
        client.head("http://host/path").body<ByteArray>()
    }

    @Test
    fun `raw source body is rendered as octet-stream and streamed`() = runTest {
        val content = renderBody { setBody(rawSourceOf("hello raw source")) }

        assertEquals(ContentType.Application.OctetStream, content.contentType)
        assertNull(content.contentLength, "no Content-Length header means chunked")
        assertEquals("hello raw source", content.toByteArray().decodeToString())
    }

    @Test
    fun `raw source body picks up the Content-Length header`() = runTest {
        val body = "hello raw source"
        val content = renderBody {
            headers[HttpHeaders.ContentLength] = body.length.toString()
            setBody(rawSourceOf(body))
        }

        assertEquals(body.length.toLong(), content.contentLength)
        assertEquals(body, content.toByteArray().decodeToString())
    }

    @Test
    fun `raw source body honours an explicit content type`() = runTest {
        val content = renderBody {
            contentType(ContentType.Application.Json)
            setBody(rawSourceOf("""{"a":1}"""))
        }

        assertEquals(ContentType.Application.Json, content.contentType)
        assertEquals("""{"a":1}""", content.toByteArray().decodeToString())
    }

    @Test
    fun `buffered source body is rendered as octet-stream`() = runTest {
        val source = Buffer().apply { writeString("hello source") }
        val content = renderBody { setBody(source) }

        assertEquals(ContentType.Application.OctetStream, content.contentType)
        assertEquals("hello source", content.toByteArray().decodeToString())
    }

    @Test
    fun `raw source body emits a single Content-Length header`() = runTest {
        val body = "hello raw source"
        val sentHeaders = renderHeaders {
            headers[HttpHeaders.ContentLength] = body.length.toString()
            setBody(rawSourceOf(body))
        }

        assertEquals(
            listOf(HttpHeaders.ContentLength to body.length.toString()),
            sentHeaders.filter { it.first == HttpHeaders.ContentLength }
        )
    }

    @Test
    fun `raw source body emits no Content-Length header when none is set`() = runTest {
        val sentHeaders = renderHeaders { setBody(rawSourceOf("hello raw source")) }

        assertTrue(
            sentHeaders.none { it.first == HttpHeaders.ContentLength },
            "Without Content-Length the body must be sent chunked, got $sentHeaders"
        )
    }

    @Test
    fun `raw source body is closed once it is fully sent`() = runTest {
        val source = trackingSourceOf("hello raw source")

        val content = renderBody { setBody(source) }
        assertEquals("hello raw source", content.toByteArray().decodeToString())

        assertTrue(source.closed, "The source should be closed once it is exhausted")
    }
}
