/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.plugins.autohead.*
import io.ktor.server.plugins.cachingheaders.*
import io.ktor.server.plugins.conditionalheaders.*
import io.ktor.server.plugins.partialcontent.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.ktor.util.date.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import kotlinx.io.*
import kotlin.test.*

class PartialContentTest {

    private val localPath = "plugins/StaticContentTest.kt"
    private val fileEtag = "etag-99"
    private val contentType = "Content-Type: application/octet-stream"
    private val content = "test_string".repeat(100).toByteArray()
    private val lastModifiedTime = getTimeMillis()

    private fun withRangeApplication(
        maxRangeCount: Int? = null,
        test: suspend ApplicationTestBuilder.() -> Unit
    ) = testApplication {
        install(ConditionalHeaders)
        install(CachingHeaders)
        install(PartialContent) {
            maxRangeCount?.let { this.maxRangeCount = it }
        }
        install(AutoHeadResponse)
        routing {
            route(localPath) {
                handle {
                    val channel = ByteReadChannel(content)
                    call.respond(
                        object : OutgoingContent.ReadChannelContent() {
                            override val contentType: ContentType = ContentType.Application.OctetStream
                            override val contentLength: Long = content.size.toLong()
                            override fun readFrom(): ByteReadChannel = channel
                        }.apply {
                            versions += LastModifiedVersion(GMTDate(lastModifiedTime))
                            versions += EntityTagVersion(fileEtag)
                        }
                    )
                }
            }
        }

        test()
    }

    @Test
    fun testCustomMaxRangeCountAcceptedRange() = withRangeApplication(maxRangeCount = 2) {
        client.get(localPath) {
            header(HttpHeaders.Range, "bytes=0-0,2-2")
        }.let { response ->
            assertEquals(HttpStatusCode.PartialContent, response.status)
            assertEquals(null, response.headers[HttpHeaders.ContentRange])
            assertNotNull(response.headers[HttpHeaders.LastModified])
            checkContentLength(response)
        }
    }

    @Test
    fun testMultipleRanges() = withRangeApplication {
        // multiple ranges
        client.get(localPath) {
            header(HttpHeaders.Range, "bytes=0-0,2-2")
        }.let { response ->
            checkContentLength(response)
            val lines = response.bodyAsText().lines()
            assertTrue(lines[0] == contentType || lines[1] == contentType)

            assertMultipart(response) { parts ->
                assertEquals(listOf("t", "t"), parts)
            }
        }
    }

    private suspend fun assertMultipart(response: HttpResponse, block: (List<String>) -> Unit) {
        assertEquals(HttpStatusCode.PartialContent, response.status)
        assertNotNull(response.headers[HttpHeaders.LastModified])
        val contentType = ContentType.parse(response.headers[HttpHeaders.ContentType]!!)
        assertTrue(contentType.match(ContentType.MultiPart.ByteRanges))
        assertNotNull(contentType.parameter("boundary"))

        val parts = response.bodyAsChannel().parseMultipart(contentType.parameter("boundary")!!)
        assertTrue { parts.isNotEmpty() }

        block(parts)
    }

    private suspend fun ByteReadChannel.parseMultipart(boundary: String): List<String> {
        val parts = ArrayList<String>()
        do {
            // according to rfc1341
            val prefix = findLineWithBoundary(boundary) ?: fail("Unexpected end")

            assert(prefix.startsWith("--$boundary"))

            if (prefix.endsWith("--$boundary--")) break
            val headers = scanHeaders()

            assertFalse(headers.isEmpty())
            assertNotNull(headers[HttpHeaders.ContentType])

            val range = headers[HttpHeaders.ContentRange]?.contentRange()
                ?: fail("Content-Range is missing in the part")

            val length = range.first.length.toInt()
            require(length > 0) { "range shouldn't be empty" }

            parts.add(
                buildString {
                    repeat(length) {
                        append(readByte().toInt().toChar())
                    }
                }
            )
        } while (true)

        return parts
    }

    private suspend fun ByteReadChannel.findLineWithBoundary(boundary: String): String? {
        do {
            val line = readUTF8Line() ?: return null
            if (line.contains(boundary)) return line
        } while (true)
    }

    private suspend fun ByteReadChannel.scanHeaders() = Headers.build {
        do {
            val line = readUTF8Line()
            if (line.isNullOrBlank()) break

            val (header, value) = line.chomp(":") { throw IOException("Illegal header line $line") }
            append(header.trimEnd(), value.trimStart())
        } while (true)
    }

    private fun String.contentRange(): Pair<LongRange, Long> {
        assertTrue { startsWith("bytes ") }

        val (range, size) = removePrefix("bytes ")
            .trimStart()
            .chomp("/") { throw IOException("Missing slash / in Content-Range header value $this") }

        val (from, to) = range
            .chomp("-") { throw IOException("Missing range delimiter in Content-Range value $this") }

        return (from.toLong()..to.toLong()) to size.toLong()
    }

    private val LongRange.length: Long
        get() = (endInclusive - start + 1).coerceAtLeast(0L)

    private inline fun String.chomp(
        separator: String,
        onMissingDelimiter: () -> Pair<String, String>
    ): Pair<String, String> {
        return when (val idx = indexOf(separator)) {
            -1 -> onMissingDelimiter()
            else -> substring(0, idx) to substring(idx + 1)
        }
    }

    private suspend fun checkContentLength(response: HttpResponse) {
        assertEquals(
            response.bodyAsBytes().size,
            response.headers[HttpHeaders.ContentLength]!!.toInt()
        )
    }
}
