/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.plugins.compression

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.test.dispatcher.*
import io.ktor.utils.io.*
import kotlin.test.Test
import kotlin.test.assertEquals

class ContentEncodingNoBodyTest {

    @Test
    fun `304 response with Content-Encoding is not decoded`() = testSuspend {
        val response = requestGzipClient(HttpMethod.Get, HttpStatusCode.NotModified)
        assertEquals(HttpStatusCode.NotModified, response.status)
        assertEquals("", response.bodyAsText())
    }

    @Test
    fun `204 response with Content-Encoding is not decoded`() = testSuspend {
        val response = requestGzipClient(HttpMethod.Get, HttpStatusCode.NoContent)
        assertEquals(HttpStatusCode.NoContent, response.status)
        assertEquals("", response.bodyAsText())
    }

    @Test
    fun `HEAD response with Content-Encoding and Content-Length is not decoded`() = testSuspend {
        val response = requestGzipClient(HttpMethod.Head, HttpStatusCode.OK, contentLength = 42)
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("", response.bodyAsText())
    }

    private suspend fun requestGzipClient(
        method: HttpMethod,
        status: HttpStatusCode,
        contentLength: Long? = null,
    ): HttpResponse {
        val client = HttpClient(MockEngine) {
            engine {
                addHandler {
                    respond(
                        ByteReadChannel.Empty,
                        status,
                        headers {
                            append(HttpHeaders.ContentEncoding, "gzip")
                            contentLength?.let { append(HttpHeaders.ContentLength, it.toString()) }
                        }
                    )
                }
            }
            install(ContentEncoding) {
                gzip()
            }
        }
        return client.use { it.request("/") { this.method = method } }
    }
}
