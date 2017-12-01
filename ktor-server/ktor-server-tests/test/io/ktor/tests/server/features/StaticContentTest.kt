package io.ktor.tests.server.features

import io.ktor.application.*
import io.ktor.content.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.testing.*
import org.junit.Test
import java.io.*
import java.nio.file.*
import kotlin.test.*

class StaticContentTest {
    val basedir = listOf(File("test"), File("ktor-server/ktor-server-tests/test")).map { File(it, "io/ktor/tests/server") }.first(File::exists)

    private operator fun File.get(relativePath: String) = File(this, relativePath)

    @Test
    fun testStaticContentBuilder() = withTestApplication {
        application.routing {
            static("files") {
                files(basedir)
            }
            static("selected") {
                staticRootFolder = basedir
                files("features")
                file("routing/RoutingBuildTest.kt")
                route("virtual") {
                    default("features/StaticContentTest.kt")
                    file("foobar.kt", "routing/RoutingBuildTest.kt")
                }
            }
            static {
                staticRootFolder = basedir
                file("foobar.kt", "routing/RoutingBuildTest.kt")
            }
        }

        // get file from nested folder
        handleAndAwait(HttpMethod.Get, "/files/features/StaticContentTest.kt").let { result ->
            assertTrue(result.requestHandled)
            assertEquals(HttpStatusCode.OK, result.response.status())
        }
        // get file from a subfolder
        handleAndAwait(HttpMethod.Get, "/selected/StaticContentTest.kt").let { result ->
            assertTrue(result.requestHandled)
            assertEquals(HttpStatusCode.OK, result.response.status())
        }
        // can't get up to containing folder
        assertFailsWith<InvalidPathException> {
            handleAndAwait(HttpMethod.Get, "/selected/../features/StaticContentTest.kt").let { result ->
                assertTrue(result.requestHandled)
                assertEquals(HttpStatusCode.OK, result.response.status())
            }
        }

        // can serve select file from other dir
        handleAndAwait(HttpMethod.Get, "/selected/routing/RoutingBuildTest.kt").let { result ->
            assertTrue(result.requestHandled)
            assertEquals(HttpStatusCode.OK, result.response.status())
        }
        // can't serve file from other dir that was not published explicitly
        handleAndAwait(HttpMethod.Get, "/selected/routing/RoutingResolveTest.kt").let { result ->
            assertFalse(result.requestHandled)
        }
        // can't serve dir itself
        handleAndAwait(HttpMethod.Get, "/selected/routing").let { result ->
            assertFalse(result.requestHandled)
        }
        // can serve file from virtual folder with a renamed file
        handleAndAwait(HttpMethod.Get, "/selected/virtual/foobar.kt").let { result ->
            assertTrue(result.requestHandled)
            assertEquals(HttpStatusCode.OK, result.response.status())
        }
        // can serve dir itself if default was given
        handleAndAwait(HttpMethod.Get, "/selected/virtual").let { result ->
            assertTrue(result.requestHandled)
            assertEquals(HttpStatusCode.OK, result.response.status())
        }
        // can serve mapped file from root folder
        handleAndAwait(HttpMethod.Get, "/foobar.kt").let { result ->
            assertTrue(result.requestHandled)
            assertEquals(HttpStatusCode.OK, result.response.status())
        }

        Unit
    }

    @Test
    fun testStaticContent() = withTestApplication {
        application.install(PartialContentSupport)
        application.install(AutoHeadResponse)

        application.routing {
            static {
                resources("io.ktor.tests.server.features")
                resources("java.util")
                route("z") {
                    staticBasePackage = "java.util"
                    resource("ArrayList.class")
                }
                files(basedir)
                route("f") {
                    files(basedir)
                }
            }
        }

        handleAndAwait(HttpMethod.Get, "/StaticContentTest.class").let { result ->
            assertTrue(result.requestHandled)
            assertEquals(HttpStatusCode.OK, result.response.status())
        }
        handleAndAwait(HttpMethod.Get, "/ArrayList.class").let { result ->
            assertTrue(result.requestHandled)
        }
        handleAndAwait(HttpMethod.Get, "/z/ArrayList.class").let { result ->
            assertTrue(result.requestHandled)
        }
        handleAndAwait(HttpMethod.Get, "/ArrayList.class2").let { result ->
            assertFalse(result.requestHandled)
        }
        handleAndAwait(HttpMethod.Get, "/features/StaticContentTest.kt").let { result ->
            assertTrue(result.requestHandled)
            assertEquals(HttpStatusCode.OK, result.response.status())
            assertEquals(RangeUnits.Bytes.unitToken, result.response.headers[HttpHeaders.AcceptRanges])
            assertNotNull(result.response.headers[HttpHeaders.LastModified])
        }
        handleAndAwait(HttpMethod.Get, "/f/features/StaticContentTest.kt").let { result ->
            assertTrue(result.requestHandled)
        }
    }

    @Test
    fun testStaticContentWrongPath() = withTestApplication {
        application.routing {
            static {
                files(basedir)
            }
        }

        listOf("../pom.xml", "../../pom.xml", "/../pom.xml", "/../../pom.xml", "/./.././../pom.xml").forEach { path ->
            assertFailsWith<InvalidPathException> {
                handleRequest(HttpMethod.Get, path).let { result ->
                    assertFalse(result.requestHandled, "Should be unhandled for path $path")
                    result.awaitCompletion()
                }
            }
        }
    }

    @Test
    fun testSendLocalFile() = withTestApplication {
        application.intercept(ApplicationCallPipeline.Call) {
            call.respond(LocalFileContent(basedir, "/features/StaticContentTest.kt".replaceSeparators()))
        }

        handleRequest(HttpMethod.Get, "/").let { result ->
            assertEquals(File(basedir, "features/StaticContentTest.kt".replaceSeparators()).readText(), result.response.content)
            assertTrue(result.requestHandled)
        }
    }

    @Test
    fun testSendLocalFilePaths() = withTestApplication {
        application.intercept(ApplicationCallPipeline.Call) {
            call.respond(LocalFileContent(basedir.toPath(), Paths.get("/features/StaticContentTest.kt".replaceSeparators())))
        }

        handleRequest(HttpMethod.Get, "/").let { result ->
            assertEquals(File(basedir, "features/StaticContentTest.kt".replaceSeparators()).readText(), result.response.content)
            assertTrue(result.requestHandled)
        }
    }

    @Test
    fun testSendLocalFileBadRelative() = withTestApplication {
        application.intercept(ApplicationCallPipeline.Call) {
            assertFailsWithSuspended<IllegalArgumentException> {
                call.respond(LocalFileContent(basedir, "/../../../../../../../../../../../../../etc/passwd"))
            }
            assertFailsWithSuspended<IllegalArgumentException> {
                call.respond(LocalFileContent(basedir.toPath(), Paths.get("../pom.xml")))
            }
            assertFailsWithSuspended<IllegalArgumentException> {
                call.respond(LocalFileContent(basedir.toPath(), Paths.get("../../pom.xml")))
            }
            assertFailsWithSuspended<IllegalArgumentException> {
                call.respond(LocalFileContent(basedir.toPath(), Paths.get("/../pom.xml")))
            }
        }

        handleRequest(HttpMethod.Get, "/").let { result ->
            assertFalse(result.requestHandled)
        }
    }

    @Test
    fun testSendLocalFileBadRelativePaths() = withTestApplication {
        application.intercept(ApplicationCallPipeline.Call) {
            assertFailsWithSuspended<IllegalArgumentException> {
                call.respond(LocalFileContent(basedir.toPath(), Paths.get("/../../../../../../../../../../../../../etc/passwd")))
            }
            assertFailsWithSuspended<IllegalArgumentException> {
                call.respond(LocalFileContent(basedir, "../pom.xml"))
            }
            assertFailsWithSuspended<IllegalArgumentException> {
                call.respond(LocalFileContent(basedir, "../../pom.xml"))
            }
            assertFailsWithSuspended<IllegalArgumentException> {
                call.respond(LocalFileContent(basedir, "/../pom.xml"))
            }
        }

        handleRequest(HttpMethod.Get, "/").let { result ->
            assertFalse(result.requestHandled)
        }
    }

    @Test
    fun testInterceptCacheControl() = withTestApplication {
        application.intercept(ApplicationCallPipeline.Infrastructure) {
            if (call.request.httpMethod == HttpMethod.Get ||
                    call.request.httpMethod == HttpMethod.Head) {
                call.response.cacheControl(CacheControl.MaxAge(300))
            }
        }

        application.intercept(ApplicationCallPipeline.Call) {
            call.respond(LocalFileContent(File(basedir, "features/StaticContentTest.kt")))
        }

        handleRequest(HttpMethod.Get, "/").let { result ->
            assertEquals(File(basedir, "features/StaticContentTest.kt".replaceSeparators()).readText(), result.response.content)
            assertEquals(listOf("max-age=300"), result.response.headers.values(HttpHeaders.CacheControl))
            assertTrue(result.requestHandled)
        }
    }

}

private fun String.replaceSeparators() = replace("/", File.separator)

private fun TestApplicationEngine.handleAndAwait(method: HttpMethod, uri: String, setup: TestApplicationRequest.() -> Unit = {}): TestApplicationCall {
    return handleRequest(method, uri, setup).also { it.awaitCompletion() }
}

private inline suspend fun <reified T> assertFailsWithSuspended(noinline block: suspend () -> Unit): T {
    val exceptionClass = T::class.java
    try {
        block()
    } catch (e: Throwable) {
        if (exceptionClass.isInstance(e)) {
            @Suppress("UNCHECKED_CAST")
            return e as T
        }

        @Suppress("INVISIBLE_MEMBER")
        asserter.fail("Expected an exception of type $exceptionClass to be thrown, but was $e")
    }

    @Suppress("INVISIBLE_MEMBER")
    asserter.fail("Expected an exception of type $exceptionClass to be thrown, but was completed successfully.")
}
