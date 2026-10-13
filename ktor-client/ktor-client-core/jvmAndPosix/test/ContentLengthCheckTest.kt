/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.cache.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.test.*
import io.ktor.utils.io.*
import kotlin.test.*

class ContentLengthCheckTest {

    @Test
    fun `saved 304 response with Content-Length and no body`() = runTest {
        val response = clientResponding(HttpStatusCode.NotModified, contentLength = 42).get("/")
        assertEquals(HttpStatusCode.NotModified, response.status)
        assertContentEquals(ByteArray(0), response.body<ByteArray>())
    }

    @Test
    fun `streaming 304 response with Content-Length and no body`() = runTest {
        val body = clientResponding(HttpStatusCode.NotModified, contentLength = 42)
            .prepareGet("/")
            .execute { it.body<ByteArray>() }
        assertContentEquals(ByteArray(0), body)
    }

    @Test
    fun `HttpCache revalidation with 304 carrying Content-Length`() = runTest {
        val client = HttpClient(MockEngine) {
            install(HttpCache)
            engine {
                addHandler { request ->
                    if (request.headers[HttpHeaders.IfNoneMatch] == "\"v1\"") {
                        respond(
                            ByteReadChannel.Empty,
                            HttpStatusCode.NotModified,
                            headersOf(HttpHeaders.ETag to listOf("\"v1\""), HttpHeaders.ContentLength to listOf("6"))
                        )
                    } else {
                        respond(
                            "cached",
                            HttpStatusCode.OK,
                            headersOf(
                                HttpHeaders.ETag to listOf("\"v1\""),
                                HttpHeaders.CacheControl to listOf("no-cache"),
                                HttpHeaders.ContentLength to listOf("6")
                            )
                        )
                    }
                }
            }
        }
        assertEquals("cached", client.get("/").bodyAsText())
        assertEquals("cached", client.get("/").bodyAsText())
    }

    @Test
    fun `saved 200 response with Content-Length mismatch still fails`() = runTest {
        val client = clientResponding(HttpStatusCode.OK, contentLength = 42, body = "ok".encodeToByteArray())
        assertFailsWith<IllegalStateException> { client.get("/") }
    }

    private fun clientResponding(
        status: HttpStatusCode,
        contentLength: Long,
        body: ByteArray = ByteArray(0),
    ): HttpClient = HttpClient(MockEngine) {
        engine {
            addHandler {
                respond(
                    ByteReadChannel(body),
                    status,
                    headersOf(HttpHeaders.ContentLength, contentLength.toString())
                )
            }
        }
    }
}
