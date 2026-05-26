/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.plugins.compression

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.tests.utils.*
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.ktor.util.*
import io.ktor.utils.io.*
import kotlin.test.*

class ContentEncodingLimitsTest {

    @Test
    fun testRejectTooLongContentEncodingChain() = testApplication {
        val plain = ByteArray(100) { it.toByte() }
        val compressed = GZipEncoder.encode(ByteReadChannel(plain))

        routing {
            get("/bomb") {
                // Advertise more codecs than the configured limit.
                call.response.headers.append(HttpHeaders.ContentEncoding, "gzip,gzip,gzip,gzip,gzip,gzip")
                call.respondBytes(compressed.toByteArray())
            }
        }

        val client = createClient {
            ContentEncoding {
                gzip()
                maxEncodingChainLength = 2
            }
        }

        assertFailsWith<ContentEncodingChainTooLongException> {
            client.get("/bomb").body<ByteArray>()
        }
    }

    @Test
    fun testRejectDecompressionBomb() = testApplication {
        // Build a small gzip payload that decompresses to a much larger one.
        val bombPlain = ByteArray(1024 * 1024) // 1 MiB of zeros
        val bombCompressed = GZipEncoder.encode(ByteReadChannel(bombPlain)).toByteArray()

        routing {
            get("/bomb") {
                call.response.headers.append(HttpHeaders.ContentEncoding, "gzip")
                call.respondBytes(bombCompressed)
            }
        }

        val client = createClient {
            ContentEncoding {
                gzip()
                maxDecodedContentLength = 16 * 1024 // 16 KiB cap, far below 1 MiB
            }
        }

        // The decoded channel is cancelled with DecodedContentTooLargeException when the limit
        // is exceeded. Downstream consumers see this either directly or wrapped in a
        // ClosedByteChannelException whose cause is our exception.
        val error = assertFails {
            client.get("/bomb").body<ByteArray>()
        }
        val rootCause = generateSequence(error) { it.cause }.last()
        assertTrue(
            rootCause is DecodedContentTooLargeException ||
                error.message?.contains("Decoded response body exceeds") == true,
            "Expected DecodedContentTooLargeException, got: $error"
        )
    }

    @Test
    fun testAllowDecodedContentBelowLimit() = testApplication {
        val plain = ByteArray(500) { it.toByte() }
        val compressed = GZipEncoder.encode(ByteReadChannel(plain)).toByteArray()

        routing {
            get("/ok") {
                call.response.headers.append(HttpHeaders.ContentEncoding, "gzip")
                call.respondBytes(compressed)
            }
        }

        val client = createClient {
            ContentEncoding {
                gzip()
                maxDecodedContentLength = (plain.size + 100).toLong()
            }
        }

        val received = client.get("/ok").body<ByteArray>()
        assertContentEquals(plain, received)
    }
}
