package org.jetbrains.ktor.tests.http

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.content.*
import org.jetbrains.ktor.features.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.testing.*
import org.jetbrains.ktor.tests.*
import org.jetbrains.ktor.util.*
import org.junit.*
import java.io.*
import java.nio.file.*
import java.util.*
import kotlin.test.*

class StaticContentTest {
    val basedir = listOf(File("test"), File("ktor-core-tests/test")).first(File::exists)

    @Test
    fun testStaticContent() {
        withTestApplication {
            application.install(PartialContentSupport)
            application.install(HeadRequestSupport)

            application.intercept(ApplicationCallPipeline.Call) { call ->
                val resolved = sequenceOf(
                        { call.resolveClasspathResource("", "org.jetbrains.ktor.tests.http") },
                        { call.resolveClasspathResource("", "java.util") },
                        { call.resolveClasspathResource("/z", "java.util") },
                        { call.resolveLocalFile("", basedir) },
                        { call.resolveLocalFile("/f", basedir) }
                ).map { it() }.firstOrNull { it != null }

                if (resolved != null) {
                    call.respond(resolved)
                }
            }

            handleRequest(HttpMethod.Get, "/StaticContentTest.class").let { result ->
                assertTrue(result.requestHandled)
                assertEquals(HttpStatusCode.OK, result.response.status())
            }
            handleRequest(HttpMethod.Get, "/ArrayList.class").let { result ->
                assertTrue(result.requestHandled)
            }
            handleRequest(HttpMethod.Get, "/z/ArrayList.class").let { result ->
                assertTrue(result.requestHandled)
            }
            handleRequest(HttpMethod.Get, "ArrayList.class").let { result ->
                assertFalse(result.requestHandled)
            }
            handleRequest(HttpMethod.Get, "/ArrayList.class2").let { result ->
                assertFalse(result.requestHandled)
            }
            handleRequest(HttpMethod.Get, "/org/jetbrains/ktor/tests/http/StaticContentTest.kt").let { result ->
                assertTrue(result.requestHandled)
                assertEquals(HttpStatusCode.OK, result.response.status())
                assertEquals(RangeUnits.Bytes.unitToken, result.response.headers[HttpHeaders.AcceptRanges])
                assertNotNull(result.response.headers[HttpHeaders.LastModified])
            }
            handleRequest(HttpMethod.Get, "/f/org/jetbrains/ktor/tests/http/StaticContentTest.kt").let { result ->
                assertTrue(result.requestHandled)
            }
        }
    }

    fun withRangeApplication(test: TestApplicationHost.(File) -> Unit) {
        withTestApplication {
            val testDir = listOf(File("test"), File("ktor-core-tests/test")).first(File::exists)

            application.install(PartialContentSupport)
            application.install(HeadRequestSupport)

            application.intercept(ApplicationCallPipeline.Call) { call ->
                call.resolveLocalFile("", testDir)?.let { call.respond(it) }
            }
            test(File(testDir, "org/jetbrains/ktor/tests/http/StaticContentTest.kt"))
        }
    }

    @Test
    fun testSingleByteRange() {
        withRangeApplication { file ->
            handleRequest(HttpMethod.Get, "/org/jetbrains/ktor/tests/http/StaticContentTest.kt", {
                addHeader(HttpHeaders.Range, "bytes=0-0")
            }).let { result ->
                assertTrue(result.requestHandled)
                assertEquals(HttpStatusCode.PartialContent, result.response.status())
                assertEquals("bytes 0-0/${file.length()}", result.response.headers[HttpHeaders.ContentRange])
                assertEquals("p", result.response.content)
                assertNotNull(result.response.headers[HttpHeaders.LastModified])
            }
        }
    }

    @Test
    fun testTwoBytesRange() {
        withRangeApplication { file ->
            handleRequest(HttpMethod.Get, "/org/jetbrains/ktor/tests/http/StaticContentTest.kt", {
                addHeader(HttpHeaders.Range, "bytes=1-2")
            }).let { result ->
                assertTrue(result.requestHandled)
                assertEquals(HttpStatusCode.PartialContent, result.response.status())
                assertEquals("ac", result.response.content)
                assertEquals("bytes 1-2/${file.length()}", result.response.headers[HttpHeaders.ContentRange])
                assertNotNull(result.response.headers[HttpHeaders.LastModified])
            }
        }
    }

    @Test
    fun testUnsatisfiableTailRange() {
        withRangeApplication { file ->
            handleRequest(HttpMethod.Get, "/org/jetbrains/ktor/tests/http/StaticContentTest.kt", {
                addHeader(HttpHeaders.Range, "bytes=-0") // unsatisfiable
            }).let { result ->
                assertTrue(result.requestHandled)
                assertEquals(HttpStatusCode.RequestedRangeNotSatisfiable.value, result.response.status()?.value)
                assertEquals("bytes */${file.length()}", result.response.headers[HttpHeaders.ContentRange])
            }
        }
    }

    @Test
    fun testUnsatisfiableRange() {
        withRangeApplication { file ->
            handleRequest(HttpMethod.Get, "/org/jetbrains/ktor/tests/http/StaticContentTest.kt", {
                addHeader(HttpHeaders.Range, "bytes=1000000-1000004")  // unsatisfiable
            }).let { result ->
                assertTrue(result.requestHandled)
                assertEquals(HttpStatusCode.RequestedRangeNotSatisfiable.value, result.response.status()?.value)
                assertEquals("bytes */${file.length()}", result.response.headers[HttpHeaders.ContentRange])
            }
        }
    }

    @Test
    fun testSyntacticallyIncorrectRange() {
        withRangeApplication { file ->
            handleRequest(HttpMethod.Get, "/org/jetbrains/ktor/tests/http/StaticContentTest.kt", {
                addHeader(HttpHeaders.Range, "bytes=1000000-7") // syntactically incorrect
            }).let { result ->
                assertTrue(result.requestHandled)
                assertEquals(HttpStatusCode.OK, result.response.status())
            }
        }
    }

    @Test
    fun testGoodAndBadTailRange() {
        withRangeApplication { file ->
            handleRequest(HttpMethod.Get, "/org/jetbrains/ktor/tests/http/StaticContentTest.kt", {
                addHeader(HttpHeaders.Range, "bytes=0-0,-0") // good + unsatisfiable
            }).let { result ->
                assertTrue(result.requestHandled)
                assertEquals(HttpStatusCode.PartialContent, result.response.status())
                assertEquals("p", result.response.content)
                assertEquals("bytes 0-0/${file.length()}", result.response.headers[HttpHeaders.ContentRange])
                assertNotNull(result.response.headers[HttpHeaders.LastModified])
            }
        }
    }

    @Test
    fun testGoodAndBadRange() {
        withRangeApplication { file ->
            handleRequest(HttpMethod.Get, "/org/jetbrains/ktor/tests/http/StaticContentTest.kt", {
                addHeader(HttpHeaders.Range, "bytes=0-0,1000000-1000004") // good + unsatisfiable
            }).let { result ->
                assertTrue(result.requestHandled)
                assertEquals(HttpStatusCode.PartialContent, result.response.status())
                assertEquals("p", result.response.content)
                assertEquals("bytes 0-0/${file.length()}", result.response.headers[HttpHeaders.ContentRange])
                assertNotNull(result.response.headers[HttpHeaders.LastModified])
            }
        }
    }

    @Test
    fun testHeadRequestRange() {
        withRangeApplication { file ->
            // head request
            handleRequest(HttpMethod.Head, "/org/jetbrains/ktor/tests/http/StaticContentTest.kt", {
                addHeader(HttpHeaders.Range, "bytes=0-0")
            }).let { result ->
                assertTrue(result.requestHandled)
                assertEquals(HttpStatusCode.OK, result.response.status())
                assertNotNull(result.response.headers[HttpHeaders.LastModified])
                assertEquals(RangeUnits.Bytes.unitToken, result.response.headers[HttpHeaders.AcceptRanges])
                assertTrue { result.response.byteContent.let { it == null || it.isEmpty() } }
            }
        }
    }

    @Test
    fun testPostRequestRange() {
        withRangeApplication { file ->
            // post request
            handleRequest(HttpMethod.Post, "/org/jetbrains/ktor/tests/http/StaticContentTest.kt", {
                addHeader(HttpHeaders.Range, "bytes=0-0")
            }).let { result ->
                assertTrue(result.requestHandled)
                assertEquals(HttpStatusCode.MethodNotAllowed.description("Method POST is not allowed with range request"), result.response.status())
            }
        }
    }

    @Test
    fun testPostNoRange() {
        withRangeApplication { file ->
            // post request with no range
            handleRequest(HttpMethod.Post, "/org/jetbrains/ktor/tests/http/StaticContentTest.kt", {

            }).let { result ->
                assertTrue(result.requestHandled)
                assertEquals(HttpStatusCode.OK, result.response.status())
            }
        }
    }

    @Test
    fun testMultipleRanges() {
        withRangeApplication { file ->
            // multiple ranges
            handleRequest(HttpMethod.Get, "/org/jetbrains/ktor/tests/http/StaticContentTest.kt", {
                addHeader(HttpHeaders.Range, "bytes=0-0,2-2")
            }).let { result ->
                assertNull(result.response.headers[HttpHeaders.ContentLength])

                assertMultipart(result) { parts ->
                    @Suppress("DEPRECATION")
                    assert(parts) {
                        sizeShouldBe(2)
                        elementAtShouldBe(0, "p")
                        elementAtShouldBe(1, "c")
                    }
                }
            }
        }
    }

    @Test
    fun testMultipleMergedRanges() {
        withRangeApplication { file ->
            // multiple ranges should be merged into one
            handleRequest(HttpMethod.Get, "/org/jetbrains/ktor/tests/http/StaticContentTest.kt", {
                addHeader(HttpHeaders.Range, "bytes=0-0,1-2")
            }).let { result ->
                assertTrue(result.requestHandled)
                assertEquals(HttpStatusCode.PartialContent, result.response.status())
                assertEquals("bytes 0-2/${file.length()}", result.response.headers[HttpHeaders.ContentRange])
                assertEquals("pac", result.response.content)
                assertNotNull(result.response.headers[HttpHeaders.LastModified])
            }
        }
    }

    @Test
    fun testStaticContentWrongPath() {
        withTestApplication {
            application.intercept(ApplicationCallPipeline.Call) { call ->
                val resolved = sequenceOf(
                        { call.resolveLocalFile("", basedir) }
                ).map { it() }.firstOrNull { it != null }

                if (resolved != null) {
                    call.respond(resolved)
                }
            }

            listOf("../pom.xml", "../../pom.xml", "/../pom.xml", "/../../pom.xml", "/./.././../pom.xml").forEach { path ->
                handleRequest(HttpMethod.Get, path).let { result ->
                    assertFalse(result.requestHandled, "Should be unhandled for path $path")
                }
            }
        }
    }

    @Test
    fun testSendLocalFile() {
        withTestApplication {
            application.intercept(ApplicationCallPipeline.Call) { call ->
                call.respond(LocalFileContent(basedir, "/org/jetbrains/ktor/tests/http/StaticContentTest.kt".replaceSeparators()))
            }

            handleRequest(HttpMethod.Get, "/").let { result ->
                assertTrue(result.requestHandled)
                assertEquals(File(basedir, "org/jetbrains/ktor/tests/http/StaticContentTest.kt".replaceSeparators()).readText(), result.response.content)
            }
        }
    }

    @Test
    fun testSendLocalFilePaths() {
        withTestApplication {
            application.intercept(ApplicationCallPipeline.Call) { call ->
                call.respond(LocalFileContent(basedir.toPath(), Paths.get("/org/jetbrains/ktor/tests/http/StaticContentTest.kt".replaceSeparators())))
            }

            handleRequest(HttpMethod.Get, "/").let { result ->
                assertTrue(result.requestHandled)
                assertEquals(File(basedir, "org/jetbrains/ktor/tests/http/StaticContentTest.kt".replaceSeparators()).readText(), result.response.content)
            }
        }
    }

    @Test
    fun testZipFileContentPaths() {
        withTestApplication {
            application.intercept(ApplicationCallPipeline.Call) { call ->
                val zip = call.resolveClasspathResource("", "java.util") as ResourceFileContent
                val pathified = ResourceFileContent(zip.zipFile.toPath(), zip.resourcePath, zip.classLoader, zip.contentType)

                call.respond(pathified)
            }

            handleRequest(HttpMethod.Get, "/ArrayList.class").let { result ->
                assertTrue(result.requestHandled)
            }
        }
    }

    @Test
    fun testSendLocalFileBadRelative() {
        withTestApplication {
            application.intercept(ApplicationCallPipeline.Call) { call ->
                assertFailsWith<IllegalArgumentException> {
                    call.respond(LocalFileContent(basedir, "/../../../../../../../../../../../../../etc/passwd"))
                }
                assertFailsWith<IllegalArgumentException> {
                    call.respond(LocalFileContent(basedir, "../pom.xml"))
                }
                assertFailsWith<IllegalArgumentException> {
                    call.respond(LocalFileContent(basedir, "../../pom.xml"))
                }
                assertFailsWith<IllegalArgumentException> {
                    call.respond(LocalFileContent(basedir, "/../pom.xml"))
                }
            }

            handleRequest(HttpMethod.Get, "/").let { result ->
                assertFalse(result.requestHandled)
            }
        }
    }

    @Test
    fun testSendLocalFileBadRelativePaths() {
        withTestApplication {
            application.intercept(ApplicationCallPipeline.Call) { call ->
                assertFailsWith<IllegalArgumentException> {
                    call.respond(LocalFileContent(basedir.toPath(), Paths.get("/../../../../../../../../../../../../../etc/passwd")))
                }
                assertFailsWith<IllegalArgumentException> {
                    call.respond(LocalFileContent(basedir.toPath(), Paths.get("../pom.xml")))
                }
                assertFailsWith<IllegalArgumentException> {
                    call.respond(LocalFileContent(basedir.toPath(), Paths.get("../../pom.xml")))
                }
                assertFailsWith<IllegalArgumentException> {
                    call.respond(LocalFileContent(basedir.toPath(), Paths.get("/../pom.xml")))
                }
            }

            handleRequest(HttpMethod.Get, "/").let { result ->
                assertFalse(result.requestHandled)
            }
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

    private fun String.replaceSeparators() = replace("/", File.separator)
}
