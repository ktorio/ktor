/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.tests.server.features

import io.ktor.application.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.testing.*
import java.io.*
import java.nio.file.*
import kotlin.test.*

class StaticContentTest {
    val basedir =
        listOf(File("jvm/test"), File("ktor-server/ktor-server-tests/jvm/test"))
            .map { File(it, "io/ktor/tests/server") }
            .first(File::exists)

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
        handleRequest(HttpMethod.Get, "/files/features/StaticContentTest.kt").let { result ->
            assertEquals(HttpStatusCode.OK, result.response.status())
        }
        // get file from a subfolder
        handleRequest(HttpMethod.Get, "/selected/StaticContentTest.kt").let { result ->
            assertEquals(HttpStatusCode.OK, result.response.status())
        }
        // can't get up to containing folder
        handleRequest(HttpMethod.Get, "/selected/../features/StaticContentTest.kt").let { result ->
            assertFalse(result.response.status()!!.isSuccess())
        }

        // can serve select file from other dir
        handleRequest(HttpMethod.Get, "/selected/routing/RoutingBuildTest.kt").let { result ->
            assertEquals(HttpStatusCode.OK, result.response.status())
        }
        // can't serve file from other dir that was not published explicitly
        handleRequest(HttpMethod.Get, "/selected/routing/RoutingResolveTest.kt").let { result ->
            assertFalse(result.response.status()!!.isSuccess())
        }
        // can't serve dir itself
        handleRequest(HttpMethod.Get, "/selected/routing").let { result ->
            assertFalse(result.response.status()!!.isSuccess())
        }
        // can serve file from virtual folder with a renamed file
        handleRequest(HttpMethod.Get, "/selected/virtual/foobar.kt").let { result ->
            assertEquals(HttpStatusCode.OK, result.response.status())
        }
        // can serve dir itself if default was given
        handleRequest(HttpMethod.Get, "/selected/virtual").let { result ->
            assertEquals(HttpStatusCode.OK, result.response.status())
        }
        // can serve mapped file from root folder
        handleRequest(HttpMethod.Get, "/foobar.kt").let { result ->
            assertEquals(HttpStatusCode.OK, result.response.status())
        }
    }

    @Test
    fun testStaticContent() = withTestApplication {
        application.install(ConditionalHeaders)
        application.install(PartialContent)
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

        handleRequest(HttpMethod.Get, "/StaticContentTest.class").let { result ->
            assertEquals(HttpStatusCode.OK, result.response.status())
        }

        handleRequest(HttpMethod.Get, "/ArrayList.class")
        handleRequest(HttpMethod.Get, "/z/ArrayList.class")
        handleRequest(HttpMethod.Get, "/ArrayList.class2")

        handleRequest(HttpMethod.Get, "/features/StaticContentTest.kt").let { result ->
            assertEquals(HttpStatusCode.OK, result.response.status())
            assertEquals(RangeUnits.Bytes.unitToken, result.response.headers[HttpHeaders.AcceptRanges])
            assertNotNull(result.response.headers[HttpHeaders.LastModified])
        }
        handleRequest(HttpMethod.Get, "/f/features/StaticContentTest.kt").let { result ->
            assertTrue(result.response.status()!!.isSuccess())
        }
    }

    @Test
    fun testStaticContentWrongPath() = withTestApplication {
        application.routing {
            static {
                files(basedir)
            }
        }

        listOf(
            "../build.gradle",
            "../../build.gradle",
            "/../build.gradle",
            "/../../build.gradle",
            "/./.././../build.gradle"
        ).forEach { path ->
            handleRequest(HttpMethod.Get, path).let { result ->
                assertFalse(result.response.status()!!.isSuccess())
            }
        }
    }

    @Test
    fun testServeEncodedFileBr() = withTestApplication {
        val ext = "json"
        val temp = File.createTempFile("testServeEncodedFile", ".$ext.br")

        File(basedir, "features/StaticContentTest.kt".replaceSeparators()).copyTo(temp, true)

        application.routing {
            static {
                preCompressed {
                    files(temp.parentFile)
                }
            }
        }

        handleRequest(HttpMethod.Get, "/${temp.nameWithoutExtension}") {
            addHeader(HttpHeaders.AcceptEncoding, "br, gzip, deflate, identity")
        }.let { result ->
            assertEquals(temp.readText(), result.response.content)
            assertEquals(ContentType.defaultForFileExtension(ext), result.response.contentType())
            assertEquals("br", result.response.headers[HttpHeaders.ContentEncoding].orEmpty())
        }
    }

    @Test
    fun testServeEncodedFileGz() = withTestApplication {
        val ext = "js"
        val temp = File.createTempFile("testServeEncodedFile", ".$ext.gz")

        File(basedir, "features/StaticContentTest.kt".replaceSeparators()).copyTo(temp, true)

        application.routing {
            static {
                preCompressed {
                    files(temp.parentFile)
                }
            }
        }

        handleRequest(HttpMethod.Get, "/${temp.nameWithoutExtension}") {
            addHeader(HttpHeaders.AcceptEncoding, "br, gzip, deflate, identity")
        }.let { result ->
            assertEquals(temp.readText(), result.response.content)
            assertEquals(ContentType.defaultForFileExtension(ext), result.response.contentType())
            assertEquals("gzip", result.response.headers[HttpHeaders.ContentEncoding].orEmpty())
        }
    }

    @Test
    // a.k.a testServeEncodedFileGzWithCompressionNoRecompress
    fun testSuppressCompressionIfAlreadyCompressed() = withTestApplication {
        application.install(Compression)
        val ext = "js"
        val temp = File.createTempFile("testServeEncodedFile", ".$ext.gz")

        File(basedir, "features/StaticContentTest.kt".replaceSeparators()).copyTo(temp, true)

        application.routing {
            static {
                preCompressed {
                    files(temp.parentFile)
                }
            }
        }

        handleRequest(HttpMethod.Get, "/${temp.nameWithoutExtension}") {
            addHeader(HttpHeaders.AcceptEncoding, "gzip")
        }.let { result ->
            assertEquals(temp.readText(), result.response.content)
            assertEquals(ContentType.defaultForFileExtension(ext), result.response.contentType())
            assertEquals("gzip", result.response.headers[HttpHeaders.ContentEncoding].orEmpty())
        }
    }

    @Test
    fun testCompressedTypesOrder() = withTestApplication {
        val ext = "js"
        val cType = ContentType.defaultForFileExtension(ext)

        val tempgz = File.createTempFile("testServeEncodedFile", ".$ext.gz")
        val publicFile = tempgz.nameWithoutExtension
        File(basedir, "features/StaticContentTest.kt".replaceSeparators()).copyTo(tempgz, true)
        tempgz.copyTo(File(tempgz.parentFile, "$publicFile.br"), true)

        application.routing {
            static("firstgz") {
                preCompressed(CompressedFileType.GZIP, CompressedFileType.BROTLI) {
                    files(tempgz.parentFile)
                }
            }
            static("firstbr") {
                preCompressed(CompressedFileType.BROTLI, CompressedFileType.GZIP) {
                    files(tempgz.parentFile)
                }
            }
        }

        handleRequest(HttpMethod.Get, "/firstgz/$publicFile") {
            addHeader(HttpHeaders.AcceptEncoding, "gzip, br")
        }.let { result ->
            assertEquals(cType, result.response.contentType())
            assertEquals("gzip", result.response.headers[HttpHeaders.ContentEncoding].orEmpty())
        }

        handleRequest(HttpMethod.Get, "/firstbr/$publicFile") {
            addHeader(HttpHeaders.AcceptEncoding, "gzip, br")
        }.let { result ->
            assertEquals(cType, result.response.contentType())
            assertEquals("br", result.response.headers[HttpHeaders.ContentEncoding].orEmpty())
        }
    }

    @Test
    fun testPreCompressedConfiguresImperatively() = withTestApplication {
        val tempFile = File.createTempFile("testServeEncodedFile", ".dummy")
        val publicFile = tempFile.nameWithoutExtension
        val gzDir = File(tempFile.parentFile, "js").also { it.mkdirs() }
        val brDir = File(tempFile.parentFile, "css").also { it.mkdirs() }

        File(basedir, "features/StaticContentTest.kt".replaceSeparators()).run {
            copyTo(File(gzDir, "$publicFile.js.gz"), true)
            copyTo(File(brDir, "$publicFile.css.br"), true)
        }

        application.routing {
            static("assets") {
                preCompressed(CompressedFileType.GZIP) {
                    files(gzDir)
                }
                preCompressed(CompressedFileType.BROTLI) {
                    files(brDir)
                }
            }
        }

        handleRequest(HttpMethod.Get, "/assets/$publicFile.js") {
            addHeader(HttpHeaders.AcceptEncoding, "gzip, br")
        }.let { result ->
            assertEquals(ContentType.defaultForFileExtension("js"), result.response.contentType())
            assertEquals("gzip", result.response.headers[HttpHeaders.ContentEncoding].orEmpty())
        }

        handleRequest(HttpMethod.Get, "/assets/$publicFile.css") {
            addHeader(HttpHeaders.AcceptEncoding, "gzip, br")
        }.let { result ->
            assertEquals(ContentType.defaultForFileExtension("css"), result.response.contentType())
            assertEquals("br", result.response.headers[HttpHeaders.ContentEncoding].orEmpty())
        }
    }

    @Test
    fun testPreCompressedConfiguresNested() = withTestApplication {
        val tempFile = File.createTempFile("testServeEncodedFile", ".dummy")
        val publicFile = tempFile.nameWithoutExtension
        val cssDir = File(tempFile.parentFile, "css").also { it.mkdirs() }

        File(basedir, "features/StaticContentTest.kt".replaceSeparators()).run {
            copyTo(File(cssDir, "$publicFile.js.gz"), true)
            copyTo(File(cssDir, "$publicFile.css.br"), true)
        }

        application.routing {
            static("assets") {
                preCompressed(CompressedFileType.GZIP) {
                    preCompressed(CompressedFileType.BROTLI) {
                        files(cssDir)
                    }
                }
            }
        }

        handleRequest(HttpMethod.Get, "/assets/$publicFile.js") {
            addHeader(HttpHeaders.AcceptEncoding, "gzip, br")
        }.let { result ->
            assertEquals(ContentType.defaultForFileExtension("js"), result.response.contentType())
            assertEquals("gzip", result.response.headers[HttpHeaders.ContentEncoding].orEmpty())
        }

        handleRequest(HttpMethod.Get, "/assets/$publicFile.css") {
            addHeader(HttpHeaders.AcceptEncoding, "gzip, br")
        }.let { result ->
            assertEquals(ContentType.defaultForFileExtension("css"), result.response.contentType())
            assertEquals("br", result.response.headers[HttpHeaders.ContentEncoding].orEmpty())
        }
    }

    @Test
    fun testSendLocalFile() = withTestApplication {
        application.intercept(ApplicationCallPipeline.Call) {
            call.respond(
                LocalFileContent(
                    basedir,
                    "/features/StaticContentTest.kt".replaceSeparators()
                )
            )
        }

        handleRequest(HttpMethod.Get, "/").let { result ->
            assertEquals(
                File(basedir, "features/StaticContentTest.kt".replaceSeparators()).readText(),
                result.response.content
            )
        }
    }

    @Test
    fun testSendLocalFilePaths() = withTestApplication {
        application.intercept(ApplicationCallPipeline.Call) {
            call.respond(
                LocalFileContent(
                    basedir.toPath(),
                    Paths.get("/features/StaticContentTest.kt".replaceSeparators())
                )
            )
        }

        handleRequest(HttpMethod.Get, "/").let { result ->
            assertEquals(
                File(basedir, "features/StaticContentTest.kt".replaceSeparators()).readText(),
                result.response.content
            )
        }
    }

    @Test
    fun testSendLocalFileBadRelative() = withTestApplication {
        application.intercept(ApplicationCallPipeline.Call) {
            assertFailsWithSuspended<Exception> {
                call.respond(
                    LocalFileContent(
                        basedir,
                        "/../../../../../../../../../../../../../etc/passwd"
                    )
                )
            }
            assertFailsWithSuspended<Exception> {
                call.respond(
                    LocalFileContent(
                        basedir,
                        "../../../../../../../../../../../../../etc/passwd"
                    )
                )
            }
            assertFailsWithSuspended<Exception> {
                call.respond(LocalFileContent(basedir.toPath(), Paths.get("../build.gradle")))
            }
            assertFailsWithSuspended<Exception> {
                call.respond(LocalFileContent(basedir.toPath(), Paths.get("../../build.gradle")))
            }
            assertFailsWithSuspended<Exception> {
                call.respond(LocalFileContent(basedir.toPath(), Paths.get("/../build.gradle")))
            }
        }

        handleRequest(HttpMethod.Get, "/").let { result ->
            assertFalse(result.response.status()!!.isSuccess())
        }
    }

    @Test
    fun testSendLocalFileBadRelativePaths() = withTestApplication {
        application.intercept(ApplicationCallPipeline.Call) {
            assertFailsWithSuspended<Exception> {
                call.respond(
                    LocalFileContent(
                        basedir.toPath(),
                        Paths.get("/../../../../../../../../../../../../../etc/passwd")
                    )
                )
            }
            assertFailsWithSuspended<Exception> {
                call.respond(
                    LocalFileContent(
                        basedir.toPath(),
                        Paths.get("../../../../../../../../../../../../../etc/passwd")
                    )
                )
            }
            assertFailsWithSuspended<Exception> {
                call.respond(LocalFileContent(basedir, "../build.gradle"))
            }
            assertFailsWithSuspended<Exception> {
                call.respond(LocalFileContent(basedir, "../../build.gradle"))
            }
            assertFailsWithSuspended<Exception> {
                call.respond(LocalFileContent(basedir, "/../build.gradle"))
            }
        }

        handleRequest(HttpMethod.Get, "/").let { result ->
            assertFalse(result.response.status()!!.isSuccess())
        }
    }

    @Test
    fun testInterceptCacheControl() = withTestApplication {
        application.intercept(ApplicationCallPipeline.Features) {
            if (call.request.httpMethod == HttpMethod.Get ||
                call.request.httpMethod == HttpMethod.Head
            ) {
                call.response.cacheControl(CacheControl.MaxAge(300))
            }
        }

        application.intercept(ApplicationCallPipeline.Call) {
            call.respond(LocalFileContent(File(basedir, "features/StaticContentTest.kt")))
        }

        handleRequest(HttpMethod.Get, "/").let { result ->
            assertEquals(
                File(basedir, "features/StaticContentTest.kt".replaceSeparators()).readText(),
                result.response.content
            )
            assertEquals(listOf("max-age=300"), result.response.headers.values(HttpHeaders.CacheControl))
        }
    }

    @Test
    fun testStaticContentPriority() = withTestApplication {
        application.routing {
            route("/before") {
                get {
                    call.respond("before")
                }
            }
            static("/") {
                defaultResource("index.html", "web-resource")
                resources("web-resource")
            }
            route("/after") {
                get {
                    call.respond("after")
                }
            }
        }

        handleRequest(HttpMethod.Get, "/before").let { result ->
            assertEquals("before", result.response.content)
        }

        handleRequest(HttpMethod.Get, "/after").let { result ->
            assertEquals("after", result.response.content)
        }
    }
}

private fun String.replaceSeparators() = replace("/", File.separator)

private suspend inline fun <reified T> assertFailsWithSuspended(noinline block: suspend () -> Unit): T {
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
