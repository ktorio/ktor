package org.jetbrains.ktor.tests.http

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.content.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.testing.*
import org.jetbrains.ktor.tests.*
import org.junit.*
import java.io.*
import java.net.*
import kotlin.test.*

class FindContainingZipFileTest {
    @Test
    fun testSimpleJar() {
        assertEquals("/dist/app.jar", findContainingZipFile(URI("jar:file:/dist/app.jar/")).path.replace('\\', '/'))
    }

    @Test
    fun testNestedJar() {
        assertEquals("/dist/app.jar", findContainingZipFile(URI("jar:jar:file:/dist/app.jar!/my/jar.jar!/")).path.replace('\\', '/'))
    }

    @Test
    fun testEscapedChars() {
        assertEquals("/Program Files/app.jar", findContainingZipFile(URI("jar:file:/Program%20Files/app.jar/")).path.replace('\\', '/'))
    }
}

class StaticContentTest {
    @Test
    fun testStaticContent() {
        withTestApplication {
            application.intercept { next ->
                val resolved = sequenceOf(
                        { resolveClasspathResource("", "org.jetbrains.ktor.tests.http") },
                        { resolveClasspathResource("", "java.util") },
                        { resolveLocalFile("", listOf(File("test"), File("ktor-core/test")).first { it.exists() }) }
                ).map { it() }.firstOrNull { it != null }

                if (resolved == null) {
                    next()
                } else {
                    response.send(resolved)
                }
            }

            handleRequest(HttpMethod.Get, "/StaticContentTest.class").let { result ->
                assertEquals(ApplicationCallResult.Handled, result.requestResult)
                assertEquals(HttpStatusCode.OK, result.response.status())
            }
            handleRequest(HttpMethod.Get, "/ArrayList.class").let { result ->
                assertEquals(ApplicationCallResult.Handled, result.requestResult)
            }
            handleRequest(HttpMethod.Get, "/ArrayList.class2").let { result ->
                assertEquals(ApplicationCallResult.Unhandled, result.requestResult)
            }
            handleRequest(HttpMethod.Get, "/org/jetbrains/ktor/tests/http/StaticContentTest.kt").let { result ->
                assertEquals(ApplicationCallResult.Handled, result.requestResult)
                assertEquals(HttpStatusCode.OK, result.response.status())
                assertEquals(RangeUnits.Bytes.unitToken, result.response.headers[HttpHeaders.AcceptRanges])
                assertNotNull(result.response.headers[HttpHeaders.LastModified])
            }
        }
    }

    @Test
    fun testRange() {
        withTestApplication {
            val testDir = listOf(File("test"), File("ktor-core/test")).first { it.exists() }
            application.intercept { next ->
                resolveLocalFile("", testDir)?.let { response.send(it) } ?: next()
            }

            handleRequest(HttpMethod.Get, "/org/jetbrains/ktor/tests/http/StaticContentTest.kt", {
                addHeader(HttpHeaders.Range, "bytes=0-0")
            }).let { result ->
                assertEquals(ApplicationCallResult.Handled, result.requestResult)
                assertEquals(HttpStatusCode.PartialContent, result.response.status())
                assertEquals("p", result.response.content)
                assertEquals("bytes 0-0/${File(testDir, "org/jetbrains/ktor/tests/http/StaticContentTest.kt").length()}", result.response.headers[HttpHeaders.ContentRange])
                assertNotNull(result.response.headers[HttpHeaders.LastModified])
            }

            handleRequest(HttpMethod.Get, "/org/jetbrains/ktor/tests/http/StaticContentTest.kt", {
                addHeader(HttpHeaders.Range, "bytes=1-2")
            }).let { result ->
                assertEquals(ApplicationCallResult.Handled, result.requestResult)
                assertEquals(HttpStatusCode.PartialContent, result.response.status())
                assertEquals("ac", result.response.content)
                assertEquals("bytes 1-2/${File(testDir, "org/jetbrains/ktor/tests/http/StaticContentTest.kt").length()}", result.response.headers[HttpHeaders.ContentRange])
                assertNotNull(result.response.headers[HttpHeaders.LastModified])
            }

            handleRequest(HttpMethod.Get, "/org/jetbrains/ktor/tests/http/StaticContentTest.kt", {
                addHeader(HttpHeaders.Range, "bytes=-0") // unsatisfiable
            }).let { result ->
                assertEquals(ApplicationCallResult.Handled, result.requestResult)
                assertEquals(HttpStatusCode.RequestedRangeNotSatisfiable, result.response.status())
            }

            handleRequest(HttpMethod.Get, "/org/jetbrains/ktor/tests/http/StaticContentTest.kt", {
                addHeader(HttpHeaders.Range, "bytes=1000000-1000004")  // unsatisfiable
            }).let { result ->
                assertEquals(ApplicationCallResult.Handled, result.requestResult)
                assertEquals(HttpStatusCode.RequestedRangeNotSatisfiable, result.response.status())
            }

            handleRequest(HttpMethod.Get, "/org/jetbrains/ktor/tests/http/StaticContentTest.kt", {
                addHeader(HttpHeaders.Range, "bytes=1000000-7") // syntactically incorrect
            }).let { result ->
                assertEquals(ApplicationCallResult.Handled, result.requestResult)
                assertEquals(HttpStatusCode.OK, result.response.status())
            }

            handleRequest(HttpMethod.Get, "/org/jetbrains/ktor/tests/http/StaticContentTest.kt", {
                addHeader(HttpHeaders.Range, "bytes=0-0,-0") // good + unsatisfiable
            }).let { result ->
                assertEquals(ApplicationCallResult.Handled, result.requestResult)
                assertEquals(HttpStatusCode.PartialContent, result.response.status())
                assertEquals("p", result.response.content)
                assertEquals("bytes 0-0/${File(testDir, "org/jetbrains/ktor/tests/http/StaticContentTest.kt").length()}", result.response.headers[HttpHeaders.ContentRange])
                assertNotNull(result.response.headers[HttpHeaders.LastModified])
            }

            handleRequest(HttpMethod.Get, "/org/jetbrains/ktor/tests/http/StaticContentTest.kt", {
                addHeader(HttpHeaders.Range, "bytes=0-0,1000000-1000004") // good + unsatisfiable
            }).let { result ->
                assertEquals(ApplicationCallResult.Handled, result.requestResult)
                assertEquals(HttpStatusCode.PartialContent, result.response.status())
                assertEquals("p", result.response.content)
                assertEquals("bytes 0-0/${File(testDir, "org/jetbrains/ktor/tests/http/StaticContentTest.kt").length()}", result.response.headers[HttpHeaders.ContentRange])
                assertNotNull(result.response.headers[HttpHeaders.LastModified])
            }

            // head request
            handleRequest(HttpMethod.Head, "/org/jetbrains/ktor/tests/http/StaticContentTest.kt", {
                addHeader(HttpHeaders.Range, "bytes=0-0")
            }).let { result ->
                assertEquals(ApplicationCallResult.Handled, result.requestResult)
                assertEquals(HttpStatusCode.OK, result.response.status())
                assertNotNull(result.response.headers[HttpHeaders.LastModified])
                assertEquals(RangeUnits.Bytes.unitToken, result.response.headers[HttpHeaders.AcceptRanges])
            }

            // post request
            handleRequest(HttpMethod.Post, "/org/jetbrains/ktor/tests/http/StaticContentTest.kt", {
                addHeader(HttpHeaders.Range, "bytes=0-0")
            }).let { result ->
                assertEquals(ApplicationCallResult.Handled, result.requestResult)
                assertEquals(HttpStatusCode.MethodNotAllowed, result.response.status())
            }

            // post request with no range
            handleRequest(HttpMethod.Post, "/org/jetbrains/ktor/tests/http/StaticContentTest.kt", {

            }).let { result ->
                assertEquals(ApplicationCallResult.Handled, result.requestResult)
                assertEquals(HttpStatusCode.OK, result.response.status())
            }
        }
    }
}
