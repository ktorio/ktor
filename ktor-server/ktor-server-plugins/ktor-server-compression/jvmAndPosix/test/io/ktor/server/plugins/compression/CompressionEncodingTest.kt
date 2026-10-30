/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.compression

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.ktor.util.*
import io.ktor.utils.io.*
import kotlinx.coroutines.coroutineScope
import kotlin.test.*

// The plugin on every platform it ships for: what the client gets back must be the encoding it names, so a body that
// only claims to be compressed fails here.
class CompressionEncodingTest {
    private val json = (1..200).joinToString(",", "[", "]") { """{"id":$it,"name":"item $it"}""" }

    @Test
    fun gzipCompressesWhenTheClientAcceptsIt() = testApplication {
        install(Compression) { gzip() }
        routing { get("/") { call.respondText(json, ContentType.Application.Json) } }

        val response = client.get("/") { header(HttpHeaders.AcceptEncoding, "gzip, br") }
        assertEquals("gzip", response.headers[HttpHeaders.ContentEncoding])
        val body = response.bodyAsBytes()
        assertTrue(body.size < json.length, "${body.size} bytes for ${json.length}")
        assertEquals(json, decode(GZipEncoder, body).decodeToString())
    }

    @Test
    fun deflateCompressesWhenTheClientAcceptsIt() = testApplication {
        install(Compression) { deflate() }
        routing { get("/") { call.respondText(json, ContentType.Application.Json) } }

        val response = client.get("/") { header(HttpHeaders.AcceptEncoding, "deflate") }
        assertEquals("deflate", response.headers[HttpHeaders.ContentEncoding])
        assertEquals(json, decode(DeflateEncoder, response.bodyAsBytes()).decodeToString())
    }

    @Test
    fun nothingIsCompressedWithoutAcceptEncoding() = testApplication {
        install(Compression) { gzip() }
        routing { get("/") { call.respondText(json, ContentType.Application.Json) } }

        val response = client.get("/")
        assertNull(response.headers[HttpHeaders.ContentEncoding])
        assertEquals(json, response.bodyAsText())
    }

    private suspend fun decode(encoder: ContentEncoder, bytes: ByteArray): ByteArray = coroutineScope {
        encoder.decode(ByteReadChannel(bytes), coroutineContext).toByteArray()
    }
}
