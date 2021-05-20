/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.server.features

import io.ktor.application.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.testing.*
import io.ktor.util.date.*
import java.io.*
import java.util.*
import kotlin.test.*

class PartialContentTest {
    private val basedir = listOf(File("jvm/test"), File("ktor-server/ktor-server-tests/jvm/test"))
        .map { File(it, "io/ktor/tests/server") }
        .first(File::exists)

    private val localPath = "features/StaticContentTest.kt"
    private val fileEtag = "etag-99"

    private fun withRangeApplication(maxRangeCount: Int? = null, test: TestApplicationEngine.(File) -> Unit): Unit =
        withTestApplication {
            application.install(ConditionalHeaders)
            application.install(CachingHeaders)
            application.install(PartialContent) {
                maxRangeCount?.let { this.maxRangeCount = it }
            }
            application.install(AutoHeadResponse)
            application.routing {
                route(localPath) {
                    handle {
                        val file = basedir.resolve(localPath)
                        if (file.isFile) {
                            call.respond(
                                LocalFileContent(file).apply {
                                    versions += EntityTagVersion(fileEtag)
                                }
                            )
                        }
                    }
                }
            }

            test(File(basedir, localPath))
        }

    @Test
    fun testCustomMaxRangeCountAccepted(): Unit = withRangeApplication(maxRangeCount = 10) {
    }

    @Test(expected = IllegalArgumentException::class)
    fun testCustomMaxRangeCountNotAccepted(): Unit = withRangeApplication(maxRangeCount = 0) {
    }

    @Test
    fun testCustomMaxRangeCountAcceptedRange(): Unit = withRangeApplication(maxRangeCount = 2) {
        handleRequest(HttpMethod.Get, localPath) {
            addHeader(HttpHeaders.Range, "bytes=0-0,2-2")
        }.let { result ->
            assertEquals(HttpStatusCode.PartialContent, result.response.status())
            assertEquals(null, result.response.headers[HttpHeaders.ContentRange])
            assertNotNull(result.response.headers[HttpHeaders.LastModified])
        }
    }

    @Test
    fun testCustomMaxRangeCountAcceptedRangeLimited(): Unit = withRangeApplication(maxRangeCount = 2) { file ->
        handleRequest(HttpMethod.Get, localPath) {
            addHeader(HttpHeaders.Range, "bytes=0-0,2-2,4-4")
        }.let { result ->
            assertEquals(HttpStatusCode.PartialContent, result.response.status())
            assertEquals("bytes 0-4/${file.length()}", result.response.headers[HttpHeaders.ContentRange])
            assertEquals(file.readChars(0, 4), result.response.content)
            assertNotNull(result.response.headers[HttpHeaders.LastModified])
        }
    }

    @Test
    fun testSingleByteRange(): Unit = withRangeApplication { file ->
        handleRequest(HttpMethod.Get, localPath) {
            addHeader(HttpHeaders.Range, "bytes=0-0")
        }.let { result ->
            assertEquals(HttpStatusCode.PartialContent, result.response.status())
            assertEquals("bytes 0-0/${file.length()}", result.response.headers[HttpHeaders.ContentRange])
            assertEquals(file.readChars(0, 0), result.response.content)
            assertNotNull(result.response.headers[HttpHeaders.LastModified])
        }
    }

    @Test
    fun testTwoBytesRange(): Unit = withRangeApplication { file ->
        handleRequest(HttpMethod.Get, localPath) {
            addHeader(HttpHeaders.Range, "bytes=1-2")
        }.let { result ->
            assertEquals(HttpStatusCode.PartialContent, result.response.status())
            assertEquals(file.readChars(1, 2), result.response.content)
            assertEquals("bytes 1-2/${file.length()}", result.response.headers[HttpHeaders.ContentRange])
            assertNotNull(result.response.headers[HttpHeaders.LastModified])
        }
    }

    @Test
    fun testUnsatisfiableTailRange(): Unit = withRangeApplication { file ->
        handleRequest(HttpMethod.Get, localPath) {
            addHeader(HttpHeaders.Range, "bytes=-0") // unsatisfiable
        }.let { result ->
            assertEquals(HttpStatusCode.RequestedRangeNotSatisfiable.value, result.response.status()?.value)
            assertEquals("bytes */${file.length()}", result.response.headers[HttpHeaders.ContentRange])
        }
    }

    @Test
    fun testUnsatisfiableRange(): Unit = withRangeApplication { file ->
        handleRequest(HttpMethod.Get, localPath) {
            addHeader(HttpHeaders.Range, "bytes=1000000-1000004") // unsatisfiable
        }.let { result ->
            assertEquals(HttpStatusCode.RequestedRangeNotSatisfiable.value, result.response.status()?.value)
            assertEquals("bytes */${file.length()}", result.response.headers[HttpHeaders.ContentRange])
        }
    }

    @Test
    fun testSyntacticallyIncorrectRange(): Unit = withRangeApplication {
        handleRequest(HttpMethod.Get, localPath) {
            addHeader(HttpHeaders.Range, "bytes=1000000-7") // syntactically incorrect
        }.let { result ->
            assertEquals(HttpStatusCode.OK, result.response.status())
        }
    }

    @Test
    fun testGoodAndBadTailRange(): Unit = withRangeApplication { file ->
        handleRequest(HttpMethod.Get, localPath) {
            addHeader(HttpHeaders.Range, "bytes=0-0,-0") // good + unsatisfiable
        }.let { result ->
            assertEquals(HttpStatusCode.PartialContent, result.response.status())
            assertEquals(file.readChars(0), result.response.content)
            assertEquals("bytes 0-0/${file.length()}", result.response.headers[HttpHeaders.ContentRange])
            assertNotNull(result.response.headers[HttpHeaders.LastModified])
        }
    }

    @Test
    fun testGoodAndBadRange(): Unit = withRangeApplication { file ->
        handleRequest(HttpMethod.Get, localPath) {
            addHeader(HttpHeaders.Range, "bytes=0-0,1000000-1000004") // good + unsatisfiable
        }.let { result ->
            assertEquals(HttpStatusCode.PartialContent, result.response.status())
            assertEquals(file.readChars(0), result.response.content)
            assertEquals("bytes 0-0/${file.length()}", result.response.headers[HttpHeaders.ContentRange])
            assertNotNull(result.response.headers[HttpHeaders.LastModified])
        }
    }

    @Test
    fun testHeadRequestRange(): Unit = withRangeApplication { _ ->
        // head request
        handleRequest(HttpMethod.Head, localPath) {
            addHeader(HttpHeaders.Range, "bytes=0-0")
        }.let { result ->
            assertEquals(HttpStatusCode.OK, result.response.status())
            assertNotNull(result.response.headers[HttpHeaders.LastModified])
            assertEquals(RangeUnits.Bytes.unitToken, result.response.headers[HttpHeaders.AcceptRanges])
            assertTrue { result.response.byteContent.let { it == null || it.isEmpty() } }
        }
    }

    @Test
    fun testPostRequestRange(): Unit = withRangeApplication {
        // post request
        handleRequest(HttpMethod.Post, localPath) {
            addHeader(HttpHeaders.Range, "bytes=0-0")
        }.let { result ->
            assertEquals(
                HttpStatusCode.MethodNotAllowed.description("Method POST is not allowed with range request"),
                result.response.status()
            )
        }
    }

    @Test
    fun testPostNoRange(): Unit = withRangeApplication {
        // post request with no range
        handleRequest(HttpMethod.Post, localPath) {
        }.let { result ->
            assertEquals(RangeUnits.Bytes.unitToken, result.response.headers[HttpHeaders.AcceptRanges])
            assertEquals(HttpStatusCode.OK, result.response.status())
        }
    }

    @Test
    fun testMultipleRanges(): Unit = withRangeApplication { file ->
        // multiple ranges
        handleRequest(HttpMethod.Get, localPath) {
            addHeader(HttpHeaders.Range, "bytes=0-0,2-2")
        }.let { result ->
            assertNull(result.response.headers[HttpHeaders.ContentLength])

            assertMultipart(result) { parts ->
                assertEquals(listOf(file.readChars(0), file.readChars(2)), parts)
            }
        }
    }

    @Test
    fun testBypassContentLength(): Unit = withRangeApplication { file ->
        handleRequest(HttpMethod.Get, localPath) {
        }.let { result ->
            assertEquals(file.length(), result.response.headers[HttpHeaders.ContentLength]!!.toLong())
        }
    }

    @Test
    fun testMultipleMergedRanges(): Unit = withRangeApplication { file ->
        // multiple ranges should be merged into one
        handleRequest(HttpMethod.Get, localPath) {
            addHeader(HttpHeaders.Range, "bytes=0-0,1-2")
        }.let { result ->
            assertEquals(HttpStatusCode.PartialContent, result.response.status())
            assertEquals("bytes 0-2/${file.length()}", result.response.headers[HttpHeaders.ContentRange])
            assertEquals(file.readChars(0, 2), result.response.content)
            assertNotNull(result.response.headers[HttpHeaders.LastModified])
        }
    }

    @Test
    fun testDontCrashWithEmptyIfRange(): Unit = withRangeApplication { file ->
        handleRequest(HttpMethod.Get, localPath) {
            addHeader(HttpHeaders.Range, "bytes=1-2")
            addHeader(HttpHeaders.IfRange, "")
        }.let { result ->
            assertEquals(HttpStatusCode.PartialContent, result.response.status())
            assertEquals("bytes 1-2/${file.length()}", result.response.headers[HttpHeaders.ContentRange])
        }
    }

    @Test
    fun testIfRangeETag(): Unit = withRangeApplication { file ->
        handleRequest(HttpMethod.Get, localPath) {
            addHeader(HttpHeaders.Range, "bytes=1-2")
            addHeader(HttpHeaders.IfRange, "\"$fileEtag\"")
        }.let { result ->
            assertEquals(HttpStatusCode.PartialContent, result.response.status())
            assertEquals("bytes 1-2/${file.length()}", result.response.headers[HttpHeaders.ContentRange])
        }

        handleRequest(HttpMethod.Get, localPath) {
            addHeader(HttpHeaders.Range, "bytes=1-2")
            addHeader(HttpHeaders.IfRange, "\"wrong-$fileEtag\"")
        }.let { result ->
            assertEquals(HttpStatusCode.OK, result.response.status())
            assertEquals(null, result.response.headers[HttpHeaders.ContentRange])
        }
    }

    @Test
    fun testIfRangeDate(): Unit = withRangeApplication { file ->
        val fileDate = GMTDate(file.lastModified())

        handleRequest(HttpMethod.Get, localPath) {
            addHeader(HttpHeaders.Range, "bytes=1-2")
            addHeader(HttpHeaders.IfRange, fileDate.toHttpDate())
        }.let { result ->
            assertEquals(HttpStatusCode.PartialContent, result.response.status())
            assertEquals("bytes 1-2/${file.length()}", result.response.headers[HttpHeaders.ContentRange])
        }

        handleRequest(HttpMethod.Get, localPath) {
            addHeader(HttpHeaders.Range, "bytes=1-2")
            addHeader(HttpHeaders.IfRange, fileDate.plus(10000).toHttpDate())
        }.let { result ->
            assertEquals(HttpStatusCode.PartialContent, result.response.status())
            assertEquals("bytes 1-2/${file.length()}", result.response.headers[HttpHeaders.ContentRange])
        }

        handleRequest(HttpMethod.Get, localPath) {
            addHeader(HttpHeaders.Range, "bytes=1-2")
            addHeader(HttpHeaders.IfRange, fileDate.minus(100000).toHttpDate())
        }.let { result ->
            assertEquals(HttpStatusCode.OK, result.response.status())
            assertEquals(null, result.response.headers[HttpHeaders.ContentRange])
        }
    }

    @Test
    fun testIfRangeWrongDate(): Unit = withRangeApplication { file ->
        val fileDate = GMTDate(file.lastModified())

        handleRequest(HttpMethod.Get, localPath) {
            addHeader(HttpHeaders.Range, "bytes=1-2")
            addHeader(HttpHeaders.IfRange, fileDate.toHttpDate().drop(15))
        }.let { result ->
            assertEquals(HttpStatusCode.OK, result.response.status())
            assertEquals(null, result.response.headers[HttpHeaders.ContentRange])
        }
    }

    private fun assertMultipart(result: TestApplicationCall, block: (List<String>) -> Unit) {
        assertEquals(HttpStatusCode.PartialContent, result.response.status())
        assertNotNull(result.response.headers[HttpHeaders.LastModified])
        val contentType = ContentType.parse(result.response.headers[HttpHeaders.ContentType]!!)
        assertTrue(contentType.match(ContentType.MultiPart.ByteRanges))
        assertNotNull(contentType.parameter("boundary"))

        val parts = result.response.content!!.reader().buffered().parseMultipart(contentType.parameter("boundary")!!)
        assertTrue { parts.isNotEmpty() }

        block(parts)
    }

    private fun BufferedReader.parseMultipart(boundary: String): List<String> {
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
                        append(read().toChar())
                    }
                }
            )
        } while (true)

        return parts
    }

    private fun BufferedReader.findLineWithBoundary(boundary: String): String? {
        do {
            val line = readLine() ?: return null
            if (line.contains(boundary)) return line
        } while (true)
    }

    private fun BufferedReader.scanHeaders() = Headers.build {
        do {
            val line = readLine()
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

    private fun File.readChars(from: Int, toInclusive: Int = from): String {
        require(from <= toInclusive)

        val result = CharArray(toInclusive - from + 1)
        reader().use { input ->
            if (from > 0) {
                assertEquals(from.toLong(), input.skip(from.toLong()))
            }
            assertEquals(result.size, input.read(result))
        }
        return String(result)
    }
}
