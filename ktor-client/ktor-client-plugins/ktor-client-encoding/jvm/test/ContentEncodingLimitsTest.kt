/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.plugins.compression

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.ktor.util.*
import io.ktor.utils.io.*
import kotlin.test.*

class ContentEncodingLimitsTest {

    private val payload = ByteArray(1024 * 1024)

    private suspend fun gzip(bytes: ByteArray, times: Int): ByteArray {
        var current = bytes
        repeat(times) {
            current = GZipEncoder.encode(ByteReadChannel(current)).toByteArray()
        }
        return current
    }

    private fun ApplicationTestBuilder.respondEncoded(path: String, encodings: Int) {
        routing {
            get(path) {
                val body = gzip(payload, encodings)
                call.response.header(HttpHeaders.ContentEncoding, List(encodings) { "gzip" }.joinToString(","))
                call.respondBytes(body)
            }
        }
    }

    @Test
    fun `chained encodings within limit are decoded`() = testApplication {
        respondEncoded("/bomb", encodings = 2)
        val client = createClient { ContentEncoding() }

        val response = client.get("/bomb")
        assertContentEquals(payload, response.body<ByteArray>())
        assertEquals(listOf("gzip", "gzip"), response.appliedDecoders)
    }

    @Test
    fun `chained encodings exceeding default limit are rejected`() = testApplication {
        respondEncoded("/bomb", encodings = 3)
        val client = createClient { ContentEncoding() }

        val cause = assertFailsWith<ContentEncodingChainTooLongException> {
            client.get("/bomb").bodyAsBytes()
        }
        assertContains(cause.message!!, "length 3")
    }

    @Test
    fun `chain length check can be disabled`() = testApplication {
        respondEncoded("/bomb", encodings = 3)
        val client = createClient {
            ContentEncoding {
                gzip()
                maxEncodingChainLength = 0
            }
        }

        assertContentEquals(payload, client.get("/bomb").body<ByteArray>())
    }

    @Test
    fun `decoded content exceeding size limit fails`() = testApplication {
        respondEncoded("/bomb", encodings = 1)
        val client = createClient {
            ContentEncoding {
                gzip()
                maxDecodedContentLength = 64 * 1024
            }
        }

        assertFailsWith<DecodedContentTooLargeException> {
            client.get("/bomb").bodyAsBytes()
        }
    }

    @Test
    fun `decoded content within size limit is read`() = testApplication {
        respondEncoded("/bomb", encodings = 1)
        val client = createClient {
            ContentEncoding {
                gzip()
                maxDecodedContentLength = payload.size.toLong()
            }
        }

        assertContentEquals(payload, client.get("/bomb").body<ByteArray>())
    }
}
