package io.ktor.tests.server.features

import io.ktor.application.*
import io.ktor.http.content.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.testing.*
import org.junit.Test
import java.io.*
import java.util.*
import kotlin.test.*

class PartialContentTest {
    val basedir = listOf(File("test"), File("ktor-server/ktor-server-tests/test")).map { File(it, "io/ktor/tests/server") }.first(File::exists)
    val localPath = "features/StaticContentTest.kt"

    fun withRangeApplication(maxRangeCount: Int? = null, test: TestApplicationEngine.(File) -> Unit) = withTestApplication {
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
                        call.respond(LocalFileContent(file))
                    }
                }
            }
        }

        test(File(basedir, localPath))
    }

    @Test
    fun testCustomMaxRangeCountAccepted() = withRangeApplication(maxRangeCount = 10) { file ->
    }

    @Test(expected = IllegalArgumentException::class)
    fun testCustomMaxRangeCountNotAccepted() = withRangeApplication(maxRangeCount = 0) { file ->
    }

    @Test
    fun testCustomMaxRangeCountAcceptedRange() = withRangeApplication(maxRangeCount = 2) { file ->
        handleRequest(HttpMethod.Get, localPath, {
            addHeader(HttpHeaders.Range, "bytes=0-0,2-2")
        }).let { result ->
            assertTrue(result.requestHandled)
            assertEquals(HttpStatusCode.PartialContent, result.response.status())
            assertEquals(null, result.response.headers[HttpHeaders.ContentRange])
            assertNotNull(result.response.headers[HttpHeaders.LastModified])
        }
    }

    @Test
    fun testCustomMaxRangeCountAcceptedRangeLimited() = withRangeApplication(maxRangeCount = 2) { file ->
        handleRequest(HttpMethod.Get, localPath, {
            addHeader(HttpHeaders.Range, "bytes=0-0,2-2,4-4")
        }).let { result ->
            assertTrue(result.requestHandled)
            assertEquals(HttpStatusCode.PartialContent, result.response.status())
            assertEquals("bytes 0-4/${file.length()}", result.response.headers[HttpHeaders.ContentRange])
            assertEquals("packa", result.response.content)
            assertNotNull(result.response.headers[HttpHeaders.LastModified])
        }
    }

    @Test
    fun testSingleByteRange() = withRangeApplication { file ->
        handleRequest(HttpMethod.Get, localPath, {
            addHeader(HttpHeaders.Range, "bytes=0-0")
        }).let { result ->
            assertTrue(result.requestHandled)
            assertEquals(HttpStatusCode.PartialContent, result.response.status())
            assertEquals("bytes 0-0/${file.length()}", result.response.headers[HttpHeaders.ContentRange])
            assertEquals("p", result.response.content)
            assertNotNull(result.response.headers[HttpHeaders.LastModified])
        }
    }

    @Test
    fun testTwoBytesRange() = withRangeApplication { file ->
        handleRequest(HttpMethod.Get, localPath, {
            addHeader(HttpHeaders.Range, "bytes=1-2")
        }).let { result ->
            assertTrue(result.requestHandled)
            assertEquals(HttpStatusCode.PartialContent, result.response.status())
            assertEquals("ac", result.response.content)
            assertEquals("bytes 1-2/${file.length()}", result.response.headers[HttpHeaders.ContentRange])
            assertNotNull(result.response.headers[HttpHeaders.LastModified])
        }
    }

    @Test
    fun testUnsatisfiableTailRange() = withRangeApplication { file ->
        handleRequest(HttpMethod.Get, localPath, {
            addHeader(HttpHeaders.Range, "bytes=-0") // unsatisfiable
        }).let { result ->
            assertTrue(result.requestHandled)
            assertEquals(HttpStatusCode.RequestedRangeNotSatisfiable.value, result.response.status()?.value)
            assertEquals("bytes */${file.length()}", result.response.headers[HttpHeaders.ContentRange])
        }
    }

    @Test
    fun testUnsatisfiableRange() = withRangeApplication { file ->
        handleRequest(HttpMethod.Get, localPath, {
            addHeader(HttpHeaders.Range, "bytes=1000000-1000004")  // unsatisfiable
        }).let { result ->
            assertTrue(result.requestHandled)
            assertEquals(HttpStatusCode.RequestedRangeNotSatisfiable.value, result.response.status()?.value)
            assertEquals("bytes */${file.length()}", result.response.headers[HttpHeaders.ContentRange])
        }
    }

    @Test
    fun testSyntacticallyIncorrectRange() = withRangeApplication {
        handleRequest(HttpMethod.Get, localPath, {
            addHeader(HttpHeaders.Range, "bytes=1000000-7") // syntactically incorrect
        }).let { result ->
            assertEquals(HttpStatusCode.OK, result.response.status())
            assertTrue(result.requestHandled)
        }
    }

    @Test
    fun testGoodAndBadTailRange() = withRangeApplication { file ->
        handleRequest(HttpMethod.Get, localPath, {
            addHeader(HttpHeaders.Range, "bytes=0-0,-0") // good + unsatisfiable
        }).let { result ->
            assertTrue(result.requestHandled)
            assertEquals(HttpStatusCode.PartialContent, result.response.status())
            assertEquals("p", result.response.content)
            assertEquals("bytes 0-0/${file.length()}", result.response.headers[HttpHeaders.ContentRange])
            assertNotNull(result.response.headers[HttpHeaders.LastModified])
        }
    }

    @Test
    fun testGoodAndBadRange() = withRangeApplication { file ->
        handleRequest(HttpMethod.Get, localPath, {
            addHeader(HttpHeaders.Range, "bytes=0-0,1000000-1000004") // good + unsatisfiable
        }).let { result ->
            assertTrue(result.requestHandled)
            assertEquals(HttpStatusCode.PartialContent, result.response.status())
            assertEquals("p", result.response.content)
            assertEquals("bytes 0-0/${file.length()}", result.response.headers[HttpHeaders.ContentRange])
            assertNotNull(result.response.headers[HttpHeaders.LastModified])
        }
    }

    @Test
    fun testHeadRequestRange() = withRangeApplication {
        // head request
        handleRequest(HttpMethod.Head, localPath, {
            addHeader(HttpHeaders.Range, "bytes=0-0")
        }).let { result ->
            assertTrue(result.requestHandled)
            assertEquals(HttpStatusCode.OK, result.response.status())
            assertNotNull(result.response.headers[HttpHeaders.LastModified])
            assertEquals(RangeUnits.Bytes.unitToken, result.response.headers[HttpHeaders.AcceptRanges])
            assertTrue { result.response.byteContent.let { it == null || it.isEmpty() } }
        }
    }

    @Test
    fun testPostRequestRange() = withRangeApplication {
        // post request
        handleRequest(HttpMethod.Post, localPath, {
            addHeader(HttpHeaders.Range, "bytes=0-0")
        }).let { result ->
            assertTrue(result.requestHandled)
            assertEquals(HttpStatusCode.MethodNotAllowed.description("Method POST is not allowed with range request"), result.response.status())
        }
    }

    @Test
    fun testPostNoRange() = withRangeApplication {
        // post request with no range
        handleRequest(HttpMethod.Post, localPath, {
        }).let { result ->
            assertTrue(result.requestHandled)
            assertEquals(RangeUnits.Bytes.unitToken, result.response.headers[HttpHeaders.AcceptRanges])
            assertEquals(HttpStatusCode.OK, result.response.status())
        }
    }

    @Test
    fun testMultipleRanges() = withRangeApplication {
        // multiple ranges
        handleRequest(HttpMethod.Get, localPath, {
            addHeader(HttpHeaders.Range, "bytes=0-0,2-2")
        }).let { result ->
            assertNull(result.response.headers[HttpHeaders.ContentLength])

            assertMultipart(result) { parts ->
                @Suppress("DEPRECATION")
                (kotlin.test.assert(parts) {
                    sizeShouldBe(2)
                    elementAtShouldBe(0, "p")
                    elementAtShouldBe(1, "c")
                })
            }
        }
    }

    @Test
    fun testMultipleMergedRanges() = withRangeApplication { file ->
        // multiple ranges should be merged into one
        handleRequest(HttpMethod.Get, localPath, {
            addHeader(HttpHeaders.Range, "bytes=0-0,1-2")
        }).let { result ->
            assertTrue(result.requestHandled)
            assertEquals(HttpStatusCode.PartialContent, result.response.status())
            assertEquals("bytes 0-2/${file.length()}", result.response.headers[HttpHeaders.ContentRange])
            assertEquals("pac", result.response.content)
            assertNotNull(result.response.headers[HttpHeaders.LastModified])
        }
    }

    private fun assertMultipart(result: TestApplicationCall, block: (List<String>) -> Unit) {
        assertTrue(result.requestHandled)
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

            parts.add(buildString {
                repeat(length) {
                    append(read().toChar())
                }
            })
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
            if (line.isNullOrBlank())
                break

            val (header, value) = line.chomp(":") { throw IOException("Illegal header line $line") }
            append(header.trimEnd(), value.trimStart())
        } while (true)
    }

    private fun String.contentRange(): Pair<LongRange, Long> {
        assertTrue { startsWith("bytes ") }
        val (range, size) = removePrefix("bytes ").trimStart().chomp("/") { throw IOException("Missing slash / in Content-Range header value $this") }
        val (from, to) = range.chomp("-") { throw IOException("Missing range delimiter in Content-Range value $this") }

        return (from.toLong()..to.toLong()) to size.toLong()
    }

    private val LongRange.length: Long
        get() = (endInclusive - start + 1).coerceAtLeast(0L)

    private inline fun String.chomp(separator: String, onMissingDelimiter: () -> Pair<String, String>): Pair<String, String> {
        val idx = indexOf(separator)
        return when (idx) {
            -1 -> onMissingDelimiter()
            else -> substring(0, idx) to substring(idx + 1)
        }
    }
}