package org.jetbrains.ktor.tests.http

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.content.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.testing.*
import org.jetbrains.ktor.tests.*
import org.junit.*
import java.io.*
import java.net.*
import java.nio.file.Paths
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
    val basedir = listOf(File("test"), File("ktor-core/test")).first { it.exists() }

    @Test
    fun testStaticContent() {
        withTestApplication {
            application.intercept { next ->
                val resolved = sequenceOf(
                        { resolveClasspathResource("", "org.jetbrains.ktor.tests.http") },
                        { resolveClasspathResource("", "java.util") },
                        { resolveClasspathResource("/z", "java.util") },
                        { resolveLocalFile("", basedir) },
                        { resolveLocalFile("/f", basedir) }
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
            handleRequest(HttpMethod.Get, "/z/ArrayList.class").let { result ->
                assertEquals(ApplicationCallResult.Handled, result.requestResult)
            }
            handleRequest(HttpMethod.Get, "ArrayList.class").let { result ->
                assertEquals(ApplicationCallResult.Unhandled, result.requestResult)
            }
            handleRequest(HttpMethod.Get, "/ArrayList.class2").let { result ->
                assertEquals(ApplicationCallResult.Unhandled, result.requestResult)
            }
            handleRequest(HttpMethod.Get, "/org/jetbrains/ktor/tests/http/StaticContentTest.kt").let { result ->
                assertEquals(ApplicationCallResult.Handled, result.requestResult)
                assertEquals(HttpStatusCode.OK, result.response.status())
                assertEquals(RangeUnits.Bytes, result.response.headers[HttpHeaders.AcceptRanges])
            }
        }
    }

    @Test
    fun testRange() {
        withTestApplication {
            application.intercept { next ->
                resolveLocalFile("", File("test"))?.let { response.send(it) } ?: next()
            }

            handleRequest(HttpMethod.Get, "/org/jetbrains/ktor/tests/http/StaticContentTest.kt", {
                addHeader(HttpHeaders.Range, "bytes=0-0")
            }).let { result ->
                assertEquals(ApplicationCallResult.Handled, result.requestResult)
                assertEquals(HttpStatusCode.PartialContent, result.response.status())
                assertEquals("p", result.response.content)
                assertEquals("bytes 0-0/${File("test/org/jetbrains/ktor/tests/http/StaticContentTest.kt").length()}", result.response.headers[HttpHeaders.ContentRange])
            }

            handleRequest(HttpMethod.Get, "/org/jetbrains/ktor/tests/http/StaticContentTest.kt", {
                addHeader(HttpHeaders.Range, "bytes=1-2")
            }).let { result ->
                assertEquals(ApplicationCallResult.Handled, result.requestResult)
                assertEquals(HttpStatusCode.PartialContent, result.response.status())
                assertEquals("ac", result.response.content)
                assertEquals("bytes 1-2/${File("test/org/jetbrains/ktor/tests/http/StaticContentTest.kt").length()}", result.response.headers[HttpHeaders.ContentRange])
            }
            handleRequest(HttpMethod.Get, "/f/org/jetbrains/ktor/tests/http/StaticContentTest.kt").let { result ->
                assertEquals(ApplicationCallResult.Handled, result.requestResult)
            }
        }
    }

    @Test
    fun testStaticContentWrongPath() {
        withTestApplication {
            application.intercept { next ->
                val resolved = sequenceOf(
                        { resolveLocalFile("", basedir) }
                ).map { it() }.firstOrNull { it != null }

                if (resolved == null) {
                    next()
                } else {
                    response.send(resolved)
                }
            }

            listOf("../pom.xml", "../../pom.xml", "/../pom.xml", "/../../pom.xml", "/./.././../pom.xml").forEach { path ->
                handleRequest(HttpMethod.Get, path).let { result ->
                    assertEquals(ApplicationCallResult.Unhandled, result.requestResult, "Should be unhandled for path $path")
                }
            }
        }
    }

    @Test
    fun testSendLocalFile() {
        withTestApplication {
            application.intercept { next ->
                response.send(LocalFileContent(basedir, "/org/jetbrains/ktor/tests/http/StaticContentTest.kt"))
            }

            handleRequest(HttpMethod.Get, "/").let { result ->
                assertEquals(ApplicationCallResult.Handled, result.requestResult)
                assertEquals(File(basedir, "org/jetbrains/ktor/tests/http/StaticContentTest.kt").readText(), result.response.content)
            }
        }
    }

    @Test
    fun testSendLocalFilePaths() {
        withTestApplication {
            application.intercept { next ->
                response.send(LocalFileContent(basedir.toPath(), Paths.get("/org/jetbrains/ktor/tests/http/StaticContentTest.kt")))
            }

            handleRequest(HttpMethod.Get, "/").let { result ->
                assertEquals(ApplicationCallResult.Handled, result.requestResult)
                assertEquals(File(basedir, "org/jetbrains/ktor/tests/http/StaticContentTest.kt").readText(), result.response.content)
            }
        }
    }

    @Test
    fun testZipFileContentPaths() {
        withTestApplication {
            application.intercept { next ->
                val zip = resolveClasspathResource("", "java.util") as ResourceFileContent
                val pathified = ResourceFileContent(zip.zipFile.toPath(), zip.resourcePath, zip.classLoader, zip.contentType)

                response.send(pathified)
            }

            handleRequest(HttpMethod.Get, "/ArrayList.class").let { result ->
                assertEquals(ApplicationCallResult.Handled, result.requestResult)
            }
        }
    }

    @Test
    fun testSendLocalFileBadRelative() {
        withTestApplication {
            application.intercept { next ->
                assertFailsWith<IllegalArgumentException> {
                    response.send(LocalFileContent(basedir, "/../../../../../../../../../../../../../etc/passwd"))
                }
                assertFailsWith<IllegalArgumentException> {
                    response.send(LocalFileContent(basedir, "../pom.xml"))
                }
                assertFailsWith<IllegalArgumentException> {
                    response.send(LocalFileContent(basedir, "../../pom.xml"))
                }
                assertFailsWith<IllegalArgumentException> {
                    response.send(LocalFileContent(basedir, "/../pom.xml"))
                }

                ApplicationCallResult.Unhandled
            }

            handleRequest(HttpMethod.Get, "/").let { result ->
                assertEquals(ApplicationCallResult.Unhandled, result.requestResult)
            }
        }
    }

    @Test
    fun testSendLocalFileBadRelativePaths() {
        withTestApplication {
            application.intercept { next ->
                assertFailsWith<IllegalArgumentException> {
                    response.send(LocalFileContent(basedir.toPath(), Paths.get("/../../../../../../../../../../../../../etc/passwd")))
                }
                assertFailsWith<IllegalArgumentException> {
                    response.send(LocalFileContent(basedir.toPath(), Paths.get("../pom.xml")))
                }
                assertFailsWith<IllegalArgumentException> {
                    response.send(LocalFileContent(basedir.toPath(), Paths.get("../../pom.xml")))
                }
                assertFailsWith<IllegalArgumentException> {
                    response.send(LocalFileContent(basedir.toPath(), Paths.get("/../pom.xml")))
                }

                ApplicationCallResult.Unhandled
            }

            handleRequest(HttpMethod.Get, "/").let { result ->
                assertEquals(ApplicationCallResult.Unhandled, result.requestResult)
            }
        }
    }
}
