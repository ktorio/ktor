package org.jetbrains.ktor.tests.http

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.content.*
import org.jetbrains.ktor.features.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.response.*
import org.jetbrains.ktor.routing.*
import org.jetbrains.ktor.testing.*
import org.jetbrains.ktor.util.*
import org.junit.*
import java.io.*
import java.util.*
import kotlin.test.*

class PartialContentTest {
    val basedir = listOf(File("test"), File("ktor-core-tests/test")).map { File(it, "org/jetbrains/ktor/tests") }.first(File::exists)
    val localPath = "http/StaticContentTest.kt"

    fun withRangeApplication(test: TestApplicationHost.(File) -> Unit) = withTestApplication {
        application.install(PartialContentSupport)
        application.install(HeadRequestSupport)
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
            assertTrue(result.requestHandled)
            assertEquals(HttpStatusCode.OK, result.response.status())
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
            if (!scanUntilBoundary(boundary)) {
                break
            }
            val headers = scanHeaders()

            if (headers.isEmpty()) {
                break
            }

            assertNotNull(headers[HttpHeaders.ContentType])
            val range = headers[HttpHeaders.ContentRange]?.contentRange() ?: fail("Content-Range is missing in the part")

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

    private fun BufferedReader.scanUntilBoundary(boundary: String): Boolean {
        do {
            val line = readLine() ?: return false
            if (line == boundary) {
                break
            }
        } while (true)

        return true
    }

    private fun BufferedReader.scanHeaders(): ValuesMap {
        val headers = ValuesMapBuilder(true)

        do {
            val line = readLine()
            if (line.isNullOrBlank()) {
                break
            }

            val (header, value) = line.chomp(":") { throw IOException("Illegal header line $line") }
            headers.append(header.trimEnd(), value.trimStart())
        } while (true)

        return headers.build()
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