/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins

import com.sun.nio.file.SensitivityWatchEventModifier
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.plugins.autohead.*
import io.ktor.server.plugins.compression.*
import io.ktor.server.plugins.conditionalheaders.*
import io.ktor.server.plugins.partialcontent.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.ktor.util.date.*
import kotlinx.coroutines.delay
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.io.FileOutputStream
import java.nio.file.FileSystems
import java.nio.file.Paths
import java.nio.file.StandardWatchEventKinds
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.name
import kotlin.io.path.pathString
import kotlin.test.*

class StaticContentTest {
    val basedir =
        listOf(File("jvm/test"), File("ktor-server/ktor-server-tests/jvm/test"))
            .map { File(it, "io/ktor/server") }
            .first(File::exists)

    @Test
    fun testResourcesVaryHeaderWithPreCompressed() = testApplication {
        routing {
            staticFiles("staticFiles", File("jvm/test-resources/public")) {
                preCompressed(CompressedFileType.BROTLI)
            }
            staticFileSystem("staticFileSystem", "jvm/test-resources/public") {
                preCompressed(CompressedFileType.BROTLI)
            }
            staticResources("staticResources", "public") {
                preCompressed(CompressedFileType.BROTLI)
            }
        }

        suspend fun testVaryHeader(path: String) {
            client.get(path).let { response ->
                assertNull(response.headers[HttpHeaders.Vary])
            }

            client.get(path) {
                headers {
                    append(HttpHeaders.AcceptEncoding, "br")
                }
            }.let { response ->
                assertEquals(HttpHeaders.AcceptEncoding, response.headers[HttpHeaders.Vary])
            }
        }

        testVaryHeader("staticFiles/nested/file-nested.txt")
        testVaryHeader("staticFileSystem/nested/file-nested.txt")
        testVaryHeader("staticResources/nested/file-nested.txt")
    }

    @Test
    @Suppress("DEPRECATION")
    fun testLegacyBuilder() = testApplication {
        routing {
            static("files") {
                files(basedir)
            }
            static("selected") {
                staticRootFolder = basedir
                files("plugins")
                file("sessions/SessionTestJvm.kt")
                route("virtual") {
                    default("plugins/StaticContentTest.kt")
                    file("foobar.kt", "sessions/SessionTestJvm.kt")
                }
            }
            static {
                staticRootFolder = basedir
                file("foobar.kt", "sessions/SessionTestJvm.kt")
            }
        }

        // get file from nested folder
        client.get("/files/plugins/StaticContentTest.kt").let { response ->
            assertEquals(HttpStatusCode.OK, response.status)
        }
        // get file from a subfolder
        client.get("/selected/StaticContentTest.kt").let { response ->
            assertEquals(HttpStatusCode.OK, response.status)
        }
        // can't get up to containing folder
        client.get("/selected/../plugins/StaticContentTest.kt").let { response ->
            assertFalse(response.status.isSuccess())
        }

        // can serve select file from other dir
        client.get("/selected/sessions/SessionTestJvm.kt").let { response ->
            assertEquals(HttpStatusCode.OK, response.status)
        }
        // can't serve file from other dir that was not published explicitly
        client.get("/selected/sessions/AutoSerializerTest.kt").let { response ->
            assertFalse(response.status.isSuccess())
        }
        // can't serve dir itself
        client.get("/selected/sessions").let { response ->
            assertFalse(response.status.isSuccess())
        }
        // can serve file from virtual folder with a renamed file
        client.get("/selected/virtual/foobar.kt").let { response ->
            assertEquals(HttpStatusCode.OK, response.status)
        }
        // can serve dir itself if default was given
        client.get("/selected/virtual").let { response ->
            assertEquals(HttpStatusCode.OK, response.status)
        }
        // can serve mapped file from root folder
        client.get("/foobar.kt").let { response ->
            assertEquals(HttpStatusCode.OK, response.status)
        }
    }

    @Test
    @Suppress("DEPRECATION")
    fun testLegacy() = testApplication {
        install(ConditionalHeaders)
        install(PartialContent)
        install(AutoHeadResponse)

        routing {
            static {
                resources("io.ktor.server.plugins")
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

        client.get("/StaticContentTest.class").let { response ->
            assertEquals(HttpStatusCode.OK, response.status)
        }

        client.get("/ArrayList.class")
        client.get("/z/ArrayList.class")
        client.get("/ArrayList.class2")

        client.get("/plugins/StaticContentTest.kt").let { response ->
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(RangeUnits.Bytes.unitToken, response.headers[HttpHeaders.AcceptRanges])
            assertNotNull(response.headers[HttpHeaders.LastModified])
        }
        client.get("/f/plugins/StaticContentTest.kt").let { response ->
            assertTrue(response.status.isSuccess())
        }
    }

    object Immutable : CacheControl(null) {
        override fun toString(): String = "immutable"
    }

    @Test
    fun testFiles() = testApplication {
        routing {
            staticFiles("static", basedir, "/plugins/StaticContentTest.kt") {
                cacheControl {
                    when (it.name) {
                        "PartialContentTest.kt" -> listOf(Immutable, CacheControl.MaxAge(1))
                        else -> emptyList()
                    }
                }
                contentType {
                    when (it.name) {
                        "PartialContentTest.kt" -> ContentType.Application.Json
                        else -> null
                    }
                }
                default("/plugins/AutoHeadResponseJvmTest.kt")
            }
            staticFiles("static_no_index", basedir, null)
        }

        val responseDefault = client.get("static")
        assertEquals(HttpStatusCode.OK, responseDefault.status)
        assertContains(responseDefault.bodyAsText(), "class StaticContentTest {")

        val responseCustom = client.get("static/plugins/PartialContentTest.kt")
        assertEquals(HttpStatusCode.OK, responseCustom.status)
        assertContains(responseCustom.bodyAsText(), "class PartialContentTest {")
        assertEquals(ContentType.Application.Json, responseCustom.contentType())
        assertEquals("immutable, max-age=1", responseCustom.headers[HttpHeaders.CacheControl])

        val responseFile = client.get("static/plugins/CookiesTest.kt")
        assertEquals(HttpStatusCode.OK, responseFile.status)
        assertContains(responseFile.bodyAsText(), "class CookiesTest {")
        assertEquals(ContentType.Application.OctetStream, responseFile.contentType())
        assertNull(responseFile.headers[HttpHeaders.CacheControl])

        val notFound = client.get("static/not-existing")
        assertEquals(HttpStatusCode.OK, notFound.status)
        assertContains(notFound.bodyAsText(), "class AutoHeadResponseJvmTest {")

        val noIndex = client.get("static_no_index")
        assertEquals(HttpStatusCode.NotFound, noIndex.status)
        val fileNoIndex = client.get("static_no_index/plugins/CookiesTest.kt")
        assertEquals(HttpStatusCode.OK, fileNoIndex.status)

        val notFoundNoDefault = client.get("static_no_index/not-existing")
        assertEquals(HttpStatusCode.NotFound, notFoundNoDefault.status)
    }

    @Test
    fun testFallback() = testApplication {
        fun <T : Any> StaticContentConfig<T>.setFallback(remotePath: String, path: String, file: String) {
            fallback { requestedPath, call ->
                if (requestedPath.endsWith(".pdf")) {
                    call.respondRedirect("/$remotePath/$path/$file")
                } else if (requestedPath.endsWith(".zip")) {
                    call.respondRedirect(file)
                }
            }
        }

        routing {
            staticFiles("staticFiles", basedir) {
                setFallback("staticFiles", "plugins", "PartialContentTest.kt")
            }
            staticResources("staticResources", "public") {
                setFallback("staticResources", "nested", "file-nested.txt")
            }
            staticFileSystem("staticFileSystem", "jvm/test-resources/public") {
                setFallback("staticFileSystem", "nested", "file-nested.txt")
            }

            staticFiles("staticFilesWithValidDefault", basedir) {
                default("/plugins/PartialContentTest.kt")
                fallback { _, _ ->
                    error("This should not be called because valid default is set")
                }
            }
            staticFiles("staticFilesWithInvalidDefault", basedir) {
                default("invalid-file.kt")
                fallback { _, call ->
                    call.respondRedirect("/staticFilesWithInvalidDefault/http/ApplicationRequestContentTestJvm.kt")
                }
            }
        }

        suspend fun testFallback(
            remotePath: String,
            path: String,
            file: String,
            body: String
        ) {
            val noFallback = client.get("$remotePath/$path/$file")
            assertEquals(HttpStatusCode.OK, noFallback.status)
            assertContains(noFallback.bodyAsText(), body)

            val fallbackPdf = client.get("$remotePath/NoSuchFile.pdf")
            assertEquals(HttpStatusCode.OK, fallbackPdf.status)
            assertContains(fallbackPdf.bodyAsText(), body)

            val fallbackZip = client.get("$remotePath/$path/NoSuchFile.zip")
            assertEquals(HttpStatusCode.OK, fallbackZip.status)
            assertContains(fallbackZip.bodyAsText(), body)

            assertEquals(HttpStatusCode.NotFound, client.get("$remotePath/NoSuchFile").status)
        }

        testFallback("staticFiles", "plugins", "PartialContentTest.kt", "class PartialContentTest {")
        testFallback("staticResources", "nested", "file-nested.txt", "file-nested.txt")
        testFallback("staticFileSystem", "nested", "file-nested.txt", "file-nested.txt")

        val validDefault = client.get("staticFilesWithValidDefault/plugins/default.kt")
        assertEquals(HttpStatusCode.OK, validDefault.status)
        assertContains(validDefault.bodyAsText(), "class PartialContentTest {")

        val invalidDefault = client.get("staticFilesWithInvalidDefault/plugins/default.kt")
        assertEquals(HttpStatusCode.OK, invalidDefault.status)
        assertContains(invalidDefault.bodyAsText(), "class ApplicationRequestContentTest {")
    }

    @Test
    fun testExtensions() = testApplication {
        routing {
            staticFiles("staticFiles", basedir) {
                extensions("kt")
            }
            staticFileSystem("staticFileSystem", "jvm/test-resources/public") {
                extensions("txt")
            }
            staticResources("staticResources", "public") {
                extensions("txt")
            }
        }

        suspend fun testExtensions(path: String, expected: String) {
            val responseFile = client.get(path)
            assertEquals(HttpStatusCode.OK, responseFile.status)
            assertContains(responseFile.bodyAsText().trim(), expected)
            assertNull(responseFile.headers[HttpHeaders.CacheControl])

            val responseFileNoExtension = client.get(path.substringBeforeLast('.'))
            assertEquals(HttpStatusCode.OK, responseFileNoExtension.status)
            assertContains(responseFileNoExtension.bodyAsText().trim(), expected)
            assertNull(responseFileNoExtension.headers[HttpHeaders.CacheControl])
        }

        testExtensions("staticFiles/plugins/CookiesTest.kt", "class CookiesTest {")
        testExtensions("staticFileSystem/file.txt", "file.txt")
        testExtensions("staticResources/file.txt", "file.txt")
    }

    @Test
    fun testExclude() = testApplication {
        routing {
            staticFiles("staticFiles", basedir, "CookiesTest.kt") {
                exclude { it.path.contains("CookiesTest") }
                exclude { it.path.contains("PartialContentTest") }
                extensions("kt")
            }
            staticFileSystem("staticFileSystem", "jvm/test-resources/public", "index.txt") {
                exclude { it.pathString.contains("ignore") }
                exclude { it.pathString.contains("secret") }
                extensions("secret.txt", "txt")
            }
            staticResources("staticResources", "public", "index.txt") {
                exclude { it.path.contains("ignore") }
                exclude { it.path.contains("secret") }
                extensions("secret.txt", "txt")
            }
        }

        suspend fun testExclude(
            path: String,
            expectedStatus: HttpStatusCode,
            expectedBody: String? = null
        ) {
            val response = client.get(path)
            assertEquals(expectedStatus, response.status)

            if (expectedBody != null) {
                assertContains(response.bodyAsText(), expectedBody)
            }
        }

        testExclude("staticFiles/plugins/CompressionTest.kt", HttpStatusCode.OK, "class CompressionTest {")
        testExclude("staticFiles/plugins/CookiesTest.kt", HttpStatusCode.Forbidden)
        testExclude("staticFiles/plugins/PartialContentTest.kt", HttpStatusCode.Forbidden)
        testExclude("staticFiles/plugins/CookiesTest", HttpStatusCode.Forbidden)
        testExclude("staticFiles/plugins/", HttpStatusCode.Forbidden)

        testExclude("staticFileSystem/file.txt", HttpStatusCode.OK, "file.txt")
        testExclude("staticFileSystem/has-fallback", HttpStatusCode.OK, "has-fallback.txt")
        testExclude("staticFileSystem/ignore.txt", HttpStatusCode.Forbidden)
        testExclude("staticFileSystem/ignore", HttpStatusCode.Forbidden)
        testExclude("staticFileSystem/ignored/", HttpStatusCode.Forbidden)

        testExclude("staticResources/file.txt", HttpStatusCode.OK, "file.txt")
        testExclude("staticResources/has-fallback", HttpStatusCode.OK, "has-fallback.txt")
        testExclude("staticResources/ignore.txt", HttpStatusCode.Forbidden)
        testExclude("staticResources/ignore", HttpStatusCode.Forbidden)
        testExclude("staticResources/ignored/", HttpStatusCode.Forbidden)
    }

    @Test
    fun testPreCompressed(@TempDir filesDir: File) = testApplication {
        val tempFile = File(filesDir, "testServeEncodedFile.txt")
        val brFile = File(filesDir, "testServeEncodedFile.txt.br")
        val gzFile = File(filesDir, "testServeEncodedFile.txt.gz")
        val gzOnlyFile = File(filesDir, "gzOnly.txt")
        val gzOnlyFileGz = File(filesDir, "gzOnly.txt.gz")
        tempFile.writeText("temp")
        brFile.writeText("temp.br")
        gzFile.writeText("temp.gz")
        gzOnlyFile.writeText("gzOnly.txt")
        gzOnlyFileGz.writeText("gzOnly.txt.gz")

        routing {
            staticFiles("staticFiles", filesDir) {
                contentType { ContentType.Text.Plain }
                preCompressed(CompressedFileType.BROTLI, CompressedFileType.GZIP)
            }

            staticFileSystem("staticFileSystem", "jvm/test-resources/public") {
                contentType { ContentType.Text.Plain }
                preCompressed(CompressedFileType.BROTLI, CompressedFileType.GZIP)
            }

            staticResources("staticResources", "public") {
                contentType { ContentType.Text.Plain }
                preCompressed(CompressedFileType.BROTLI, CompressedFileType.GZIP)
            }
        }

        suspend fun testPreCompressed(
            path: String,
            content: String,
            type: CompressedFileType?,
            expectEncodedResponse: Boolean = true,
        ) {
            val response = client.get(path) {
                if (type != null) {
                    header(HttpHeaders.AcceptEncoding, type.encoding)
                }
            }
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(content, response.bodyAsText().trim())
            if (type != null && expectEncodedResponse) {
                assertEquals(type.encoding, response.headers[HttpHeaders.ContentEncoding])
            }
            assertEquals(ContentType.Text.Plain, response.contentType()!!.withoutParameters())
        }

        testPreCompressed("staticFiles/testServeEncodedFile.txt", "temp", null)
        testPreCompressed("staticFiles/testServeEncodedFile.txt", "temp.br", CompressedFileType.BROTLI)
        testPreCompressed("staticFiles/testServeEncodedFile.txt", "temp.gz", CompressedFileType.GZIP)
        testPreCompressed("staticFiles/gzOnly.txt", "gzOnly.txt.gz", CompressedFileType.GZIP)
        testPreCompressed("staticFiles/gzOnly.txt", "gzOnly.txt", CompressedFileType.BROTLI, false)

        testPreCompressed("staticFileSystem", "index", null)
        testPreCompressed("staticFileSystem", "index.gz", CompressedFileType.GZIP)
        testPreCompressed("staticFileSystem", "index.br", CompressedFileType.BROTLI)
        testPreCompressed("staticFileSystem/nested/file-nested.txt", "file-nested.txt", null)
        testPreCompressed("staticFileSystem/nested/file-nested.txt", "file-nested.txt.br", CompressedFileType.BROTLI)
        testPreCompressed("staticFileSystem/nested/file-nested.txt", "file-nested.txt", CompressedFileType.GZIP, false)

        testPreCompressed("staticResources", "index", null)
        testPreCompressed("staticResources", "index.gz", CompressedFileType.GZIP)
        testPreCompressed("staticResources", "index.br", CompressedFileType.BROTLI)
        testPreCompressed("staticResources/nested/file-nested.txt", "file-nested.txt", null)
        testPreCompressed("staticResources/nested/file-nested.txt", "file-nested.txt.br", CompressedFileType.BROTLI)
        testPreCompressed("staticResources/nested/file-nested.txt", "file-nested.txt", CompressedFileType.GZIP, false)
    }

    @Test
    fun testAutoHead() = testApplication {
        routing {
            staticFiles("staticFiles", basedir, "/plugins/StaticContentTest.kt") {
                enableAutoHeadResponse()
            }

            staticFileSystem("staticFileSystem", "jvm/test-resources/public") {
                enableAutoHeadResponse()
            }
            staticResources("staticResources", "public") {
                enableAutoHeadResponse()
            }
        }

        suspend fun testHeadResponse(path: String) {
            val response = client.head(path)
            assertEquals(HttpStatusCode.OK, response.status)
            assertTrue(response.bodyAsText().isEmpty())
            assertTrue(response.contentLength()!! > 0)
        }

        val responseFilesIndex = client.get("staticFiles")
        assertEquals(HttpStatusCode.OK, responseFilesIndex.status)
        assertContains(responseFilesIndex.bodyAsText(), "class StaticContentTest {")

        testHeadResponse("staticFiles")
        testHeadResponse("staticFiles/plugins/CookiesTest.kt")

        val responseFilesNotFound = client.head("staticFiles/not-existing")
        assertEquals(HttpStatusCode.NotFound, responseFilesNotFound.status)

        val responsePathIndex = client.get("staticFileSystem")
        assertEquals(HttpStatusCode.OK, responsePathIndex.status)
        assertEquals("index", responsePathIndex.bodyAsText().trim())
        assertEquals(ContentType.Text.Html, responsePathIndex.contentType()!!.withoutParameters())

        testHeadResponse("staticFileSystem")
        testHeadResponse("staticFileSystem/file.txt")

        val responsePathNotFound = client.head("staticFileSystem/not-existing")
        assertEquals(HttpStatusCode.NotFound, responsePathNotFound.status)

        val responseResourcesIndex = client.get("staticResources")
        assertEquals(HttpStatusCode.OK, responseResourcesIndex.status)
        assertEquals("index", responseResourcesIndex.bodyAsText().trim())
        assertEquals(ContentType.Text.Html, responseResourcesIndex.contentType()!!.withoutParameters())

        testHeadResponse("staticResources")
        testHeadResponse("staticResources/file.txt")

        val responseResourcesNotFound = client.head("staticResources/not-existing")
        assertEquals(HttpStatusCode.NotFound, responseResourcesNotFound.status)
    }

    @Test
    fun testModifier() = testApplication {
        routing {
            staticFiles("staticFiles", basedir, "/plugins/StaticContentTest.kt") {
                modify { url, call ->
                    call.response.headers.append(HttpHeaders.ETag, url.name)
                }
            }

            staticFileSystem("staticFileSystem", "jvm/test-resources/public") {
                modify { path, call ->
                    call.response.headers.append(HttpHeaders.ETag, path.fileName.toString())
                }
            }

            staticResources("staticResources", "public") {
                modify { url, call ->
                    call.response.headers.append(HttpHeaders.ETag, url.path.substringAfterLast('/'))
                }
            }
        }

        suspend fun testEtag(path: String, content: String) {
            val response = client.get(path)
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(content, response.headers[HttpHeaders.ETag])
        }

        testEtag("staticFiles", "StaticContentTest.kt")
        testEtag("staticFiles/plugins/CookiesTest.kt", "CookiesTest.kt")

        testEtag("staticFileSystem", "index.html")
        testEtag("staticFileSystem/file.txt", "file.txt")

        testEtag("staticResources", "index.html")
        testEtag("staticResources/file.txt", "file.txt")
    }

    @Test
    fun testWithIndexAndDefault() = testApplication {
        var respondCount = 0
        install(
            createApplicationPlugin("test") {
                onCallRespond { _ -> respondCount++ }
            }
        )

        routing {
            staticFiles("staticFiles", File("jvm/test-resources/public"), "file.txt") {
                default("default.txt")
            }

            staticFileSystem("staticFileSystem", "jvm/test-resources/public", "file.txt") {
                default("default.txt")
            }

            staticResources("staticResources", "public", "file.txt") {
                default("default.txt")
            }
        }

        suspend fun testIndexAndDefault(path: String, expected: String) {
            respondCount = 0
            val response = client.get(path)

            assertEquals(HttpStatusCode.OK, response.status)
            assertContains(response.bodyAsText(), expected)
            assertEquals(1, respondCount)
        }

        testIndexAndDefault("staticFiles", "file.txt")
        testIndexAndDefault("staticFiles/not-existing", "default")

        testIndexAndDefault("staticFileSystem", "file.txt")
        testIndexAndDefault("staticFileSystem/not-existing", "default")

        testIndexAndDefault("staticResources", "file.txt")
        testIndexAndDefault("staticResources/not-existing", "default")
    }

    @Test
    fun testPath() = testApplication {
        routing {
            staticFileSystem("static", "jvm/test-resources/public") {
                cacheControl {
                    when {
                        it.name.endsWith("file-nested.txt") -> listOf(Immutable, CacheControl.MaxAge(1))
                        else -> emptyList()
                    }
                }
                contentType {
                    when {
                        it.name.endsWith("file-nested.txt") -> ContentType.Application.Json
                        else -> null
                    }
                }
                default("default.txt")
            }
            staticFileSystem("static_no_index", "jvm/test-resources/public", null)
            staticFileSystem("static_no_base_path", null)
        }

        val responseIndex = client.get("static")
        assertEquals(HttpStatusCode.OK, responseIndex.status)
        assertEquals("index", responseIndex.bodyAsText().trim())
        assertEquals(ContentType.Text.Html, responseIndex.contentType()!!.withoutParameters())

        val responseIndexNested = client.get("static/nested")
        assertEquals(HttpStatusCode.OK, responseIndexNested.status)
        assertEquals("nested index", responseIndexNested.bodyAsText().trim())

        val responseCustom = client.get("static/nested/file-nested.txt")
        assertEquals(HttpStatusCode.OK, responseCustom.status)
        assertEquals("file-nested.txt", responseCustom.bodyAsText().trim())
        assertEquals(ContentType.Application.Json, responseCustom.contentType())
        assertEquals("immutable, max-age=1", responseCustom.headers[HttpHeaders.CacheControl])

        val responseFile = client.get("static/file.txt")
        assertEquals(HttpStatusCode.OK, responseFile.status)
        assertEquals("file.txt", responseFile.bodyAsText().trim())
        assertEquals(ContentType.Text.Plain, responseFile.contentType()!!.withoutParameters())
        assertNull(responseFile.headers[HttpHeaders.CacheControl])

        val notFound = client.get("static/not-existing")
        assertEquals(HttpStatusCode.OK, notFound.status)
        assertEquals("default", notFound.bodyAsText().trim())

        val noIndex = client.get("static_no_index")
        assertEquals(HttpStatusCode.NotFound, noIndex.status)
        val fileNoIndex = client.get("static_no_index/file.txt")
        assertEquals(HttpStatusCode.OK, fileNoIndex.status)
        val notFoundNoDefault = client.get("static_no_index/not-existing")
        assertEquals(HttpStatusCode.NotFound, notFoundNoDefault.status)

        val noBasePath = client.get("static_no_base_path/jvm/test-resources/public/file.txt")
        assertEquals(HttpStatusCode.OK, noBasePath.status)
        assertEquals("file.txt", noBasePath.bodyAsText().trim())
        val noBasePathIndex = client.get("static_no_base_path/jvm/test-resources/public")
        assertEquals(HttpStatusCode.OK, noBasePathIndex.status)
        assertEquals("index", noBasePathIndex.bodyAsText().trim())
    }

    @Test
    fun testPathFromZip() = testApplication {
        routing {
            staticZip(
                remotePath = "static",
                basePath = "public",
                zip = Paths.get("jvm/test-resources/public.zip")
            ) {
                default("default.txt")
            }
        }

        val responseIndex = client.get("static")
        assertEquals(HttpStatusCode.OK, responseIndex.status)
        assertEquals("index", responseIndex.bodyAsText().trim())
        assertEquals(ContentType.Text.Html, responseIndex.contentType()!!.withoutParameters())

        val responseIndexNested = client.get("static/nested")
        assertEquals(HttpStatusCode.OK, responseIndexNested.status)
        assertEquals("nested index", responseIndexNested.bodyAsText().trim())

        val responseFile = client.get("static/file.txt")
        assertEquals(HttpStatusCode.OK, responseFile.status)
        assertEquals("file.txt", responseFile.bodyAsText().trim())
        assertEquals(ContentType.Text.Plain, responseFile.contentType()!!.withoutParameters())
        assertNull(responseFile.headers[HttpHeaders.CacheControl])

        val notFound = client.get("static/not-existing")
        assertEquals(HttpStatusCode.OK, notFound.status)
        assertEquals("default", notFound.bodyAsText().trim())
    }

    @Test
    fun testResources() = testApplication {
        routing {
            staticResources("static", "public") {
                cacheControl {
                    when {
                        it.path.endsWith("file-nested.txt") -> listOf(Immutable, CacheControl.MaxAge(1))
                        else -> emptyList()
                    }
                }
                contentType {
                    when {
                        it.path.endsWith("file-nested.txt") -> ContentType.Application.Json
                        else -> null
                    }
                }
                default("default.txt")
            }
            staticResources("static_no_index", "public", null)
        }

        val responseIndex = client.get("static")
        assertEquals(HttpStatusCode.OK, responseIndex.status)
        assertEquals("index", responseIndex.bodyAsText().trim())
        assertEquals(ContentType.Text.Html, responseIndex.contentType()!!.withoutParameters())

        val responseIndexNested = client.get("static/nested")
        assertEquals(HttpStatusCode.OK, responseIndexNested.status)
        assertEquals("nested index", responseIndexNested.bodyAsText().trim())

        val responseCustom = client.get("static/nested/file-nested.txt")
        assertEquals(HttpStatusCode.OK, responseCustom.status)
        assertEquals("file-nested.txt", responseCustom.bodyAsText().trim())
        assertEquals(ContentType.Application.Json, responseCustom.contentType())
        assertEquals("immutable, max-age=1", responseCustom.headers[HttpHeaders.CacheControl])

        val responseFile = client.get("static/file.txt")
        assertEquals(HttpStatusCode.OK, responseFile.status)
        assertEquals("file.txt", responseFile.bodyAsText().trim())
        assertEquals(ContentType.Text.Plain, responseFile.contentType()!!.withoutParameters())
        assertNull(responseFile.headers[HttpHeaders.CacheControl])

        val notFound = client.get("static/not-existing")
        assertEquals(HttpStatusCode.OK, notFound.status)
        assertEquals("default", notFound.bodyAsText().trim())

        val noIndex = client.get("static_no_index")
        assertEquals(HttpStatusCode.NotFound, noIndex.status)
        val fileNoIndex = client.get("static_no_index/file.txt")
        assertEquals(HttpStatusCode.OK, fileNoIndex.status)
        val notFoundNoDefault = client.get("static_no_index/not-existing")
        assertEquals(HttpStatusCode.NotFound, notFoundNoDefault.status)
    }

    @Test
    @Suppress("DEPRECATION")
    fun testLegacyNullJarFile() = testApplication {
        routing {
            static {
                resources()
            }
        }

        client.get("/").let { response ->
            assertEquals(response.status, HttpStatusCode.NotFound)
        }

        client.get("../build.gradle").let { response ->
            assertEquals(response.status, HttpStatusCode.BadRequest)
        }
    }

    @Test
    @Suppress("DEPRECATION")
    fun testLegacyBadPath() = testApplication {
        routing {
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
            client.get(path).let { response ->
                assertFalse(response.status.isSuccess())
            }
        }
    }

    @Test
    @Suppress("DEPRECATION")
    fun testLegacyServeEncodedFileBr(@TempDir tempDir: File) = testApplication {
        val ext = "json"

        val originalFile = File(basedir, "plugins/StaticContentTest.kt".replaceSeparators())
        originalFile.copyTo(tempDir.resolve("file.$ext.br"), true)
        originalFile.copyTo(tempDir.resolve("file.$ext"), true)

        routing {
            static {
                preCompressed {
                    files(tempDir)
                }
            }
        }

        client.get("/file.$ext") {
            header(HttpHeaders.AcceptEncoding, "br, gzip, deflate, identity")
        }.let { response ->
            assertEquals(originalFile.readText(), response.bodyAsText())
            assertEquals(ContentType.defaultForFileExtension(ext), response.contentType())
            assertEquals("br", response.headers[HttpHeaders.ContentEncoding].orEmpty())
        }
    }

    @Test
    @Suppress("DEPRECATION")
    fun testLegacyServeEncodedFileGz(@TempDir tempDir: File) = testApplication {
        val ext = "js"

        val originalFile = File(basedir, "plugins/StaticContentTest.kt".replaceSeparators())
        originalFile.copyTo(tempDir.resolve("file.$ext.gz"), true)
        originalFile.copyTo(tempDir.resolve("file.$ext"), true)

        routing {
            static {
                preCompressed {
                    files(tempDir)
                }
            }
        }

        client.get("/file.$ext") {
            header(HttpHeaders.AcceptEncoding, "br, gzip, deflate, identity")
        }.let { response ->
            assertEquals(originalFile.readText(), response.bodyAsText())
            assertEquals(ContentType.defaultForFileExtension(ext), response.contentType())
            assertEquals("gzip", response.headers[HttpHeaders.ContentEncoding].orEmpty())
        }
    }

    // a.k.a testServeEncodedFileGzWithCompressionNoRecompress
    @Test
    @Suppress("DEPRECATION")
    fun testLegacySuppressCompressionIfAlreadyCompressed(@TempDir tempDir: File) = testApplication {
        install(Compression)
        val ext = "js"

        val originalFile = File(basedir, "plugins/StaticContentTest.kt".replaceSeparators())
        originalFile.copyTo(tempDir.resolve("file.$ext.gz"), true)
        originalFile.copyTo(tempDir.resolve("file.$ext"), true)

        routing {
            static {
                preCompressed {
                    files(tempDir)
                }
            }
        }

        client.get("/file.$ext") {
            header(HttpHeaders.AcceptEncoding, "gzip")
        }.let { response ->
            assertEquals(originalFile.readText(), response.bodyAsText())
            assertEquals(ContentType.defaultForFileExtension(ext), response.contentType())
            assertEquals("gzip", response.headers[HttpHeaders.ContentEncoding].orEmpty())
        }
    }

    @Test
    @Suppress("DEPRECATION")
    fun testLegacyCompressedTypesOrder(@TempDir tempDir: File) = testApplication {
        val ext = "js"
        val cType = ContentType.defaultForFileExtension(ext)

        val originalFile = File(basedir, "plugins/StaticContentTest.kt".replaceSeparators())
        originalFile.copyTo(tempDir.resolve("file.$ext.br"), true)
        originalFile.copyTo(tempDir.resolve("file.$ext.gz"), true)
        originalFile.copyTo(tempDir.resolve("file.$ext"), true)

        routing {
            static("firstgz") {
                preCompressed(CompressedFileType.GZIP, CompressedFileType.BROTLI) {
                    files(tempDir)
                }
            }
            static("firstbr") {
                preCompressed(CompressedFileType.BROTLI, CompressedFileType.GZIP) {
                    files(tempDir)
                }
            }
        }

        client.get("/firstgz/file.$ext") {
            header(HttpHeaders.AcceptEncoding, "gzip, br")
        }.let { response ->
            assertEquals(cType, response.contentType())
            assertEquals("gzip", response.headers[HttpHeaders.ContentEncoding].orEmpty())
        }

        client.get("/firstbr/file.$ext") {
            header(HttpHeaders.AcceptEncoding, "gzip, br")
        }.let { response ->
            assertEquals(cType, response.contentType())
            assertEquals("br", response.headers[HttpHeaders.ContentEncoding].orEmpty())
        }
    }

    @Test
    @Suppress("DEPRECATION")
    fun testLegacyPreCompressedConfiguresImperatively(@TempDir tempDir: File) = testApplication {
        val gzDir = File(tempDir, "js").also { it.mkdirs() }
        val brDir = File(tempDir, "css").also { it.mkdirs() }

        File(basedir, "plugins/StaticContentTest.kt".replaceSeparators()).run {
            copyTo(File(gzDir, "file.js"), true)
            copyTo(File(gzDir, "file.js.gz"), true)
            copyTo(File(brDir, "file.css"), true)
            copyTo(File(brDir, "file.css.br"), true)
        }

        routing {
            static("assets") {
                preCompressed(CompressedFileType.GZIP) {
                    files(gzDir)
                }
                preCompressed(CompressedFileType.BROTLI) {
                    files(brDir)
                }
            }
        }

        client.get("/assets/file.js") {
            header(HttpHeaders.AcceptEncoding, "gzip, br")
        }.let { response ->
            assertEquals(ContentType.defaultForFileExtension("js"), response.contentType())
            assertEquals("gzip", response.headers[HttpHeaders.ContentEncoding].orEmpty())
        }

        client.get("/assets/file.css") {
            header(HttpHeaders.AcceptEncoding, "gzip, br")
        }.let { response ->
            assertEquals(ContentType.defaultForFileExtension("css"), response.contentType())
            assertEquals("br", response.headers[HttpHeaders.ContentEncoding].orEmpty())
        }
    }

    @Test
    @Suppress("DEPRECATION")
    fun testLegacyPreCompressedConfiguresNested(@TempDir tempDir: File) = testApplication {
        val cssDir = File(tempDir, "css").also { it.mkdirs() }

        File(basedir, "plugins/StaticContentTest.kt".replaceSeparators()).run {
            copyTo(File(cssDir, "file.js"), true)
            copyTo(File(cssDir, "file.js.gz"), true)
            copyTo(File(cssDir, "file.css"), true)
            copyTo(File(cssDir, "file.css.br"), true)
        }

        routing {
            static("assets") {
                preCompressed(CompressedFileType.GZIP) {
                    preCompressed(CompressedFileType.BROTLI) {
                        files(cssDir)
                    }
                }
            }
        }

        client.get("/assets/file.js") {
            header(HttpHeaders.AcceptEncoding, "gzip, br")
        }.let { response ->
            assertEquals(ContentType.defaultForFileExtension("js"), response.contentType())
            assertEquals("gzip", response.headers[HttpHeaders.ContentEncoding].orEmpty())
        }

        client.get("/assets/file.css") {
            header(HttpHeaders.AcceptEncoding, "gzip, br")
        }.let { response ->
            assertEquals(ContentType.defaultForFileExtension("css"), response.contentType())
            assertEquals("br", response.headers[HttpHeaders.ContentEncoding].orEmpty())
        }
    }

    @Test
    fun testSendLocalFile() = testApplication {
        application {
            intercept(ApplicationCallPipeline.Call) {
                call.respond(
                    LocalFileContent(
                        basedir,
                        "/plugins/StaticContentTest.kt".replaceSeparators()
                    )
                )
            }
        }

        client.get("/").let { response ->
            assertEquals(
                File(basedir, "plugins/StaticContentTest.kt".replaceSeparators()).readText(),
                response.bodyAsText()
            )
        }
    }

    @Test
    fun testSendLocalFilePaths() = testApplication {
        application {
            intercept(ApplicationCallPipeline.Call) {
                call.respond(
                    LocalPathContent(
                        basedir.toPath(),
                        Paths.get("/plugins/StaticContentTest.kt".replaceSeparators())
                    )
                )
            }
        }

        client.get("/").let { response ->
            assertEquals(
                File(basedir, "plugins/StaticContentTest.kt".replaceSeparators()).readText(),
                response.bodyAsText()
            )
        }
    }

    @Test
    fun testSendLocalFileBadRelative() = testApplication {
        application {
            intercept(ApplicationCallPipeline.Call) {
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
                    call.respond(LocalPathContent(basedir.toPath(), Paths.get("../build.gradle")))
                }
                assertFailsWithSuspended<Exception> {
                    call.respond(LocalPathContent(basedir.toPath(), Paths.get("../../build.gradle")))
                }
                assertFailsWithSuspended<Exception> {
                    call.respond(LocalPathContent(basedir.toPath(), Paths.get("/../build.gradle")))
                }
            }
        }

        client.get("/").let { response ->
            assertFalse(response.status.isSuccess())
        }
    }

    @Test
    fun testSendLocalFileBadRelativePaths() = testApplication {
        application {
            intercept(ApplicationCallPipeline.Call) {
                assertFailsWithSuspended<Exception> {
                    call.respond(
                        LocalPathContent(
                            basedir.toPath(),
                            Paths.get("/../../../../../../../../../../../../../etc/passwd")
                        )
                    )
                }
                assertFailsWithSuspended<Exception> {
                    call.respond(
                        LocalPathContent(
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
        }

        client.get("/").let { response ->
            assertFalse(response.status.isSuccess())
        }
    }

    @Test
    fun testInterceptCacheControl() = testApplication {
        application {
            intercept(ApplicationCallPipeline.Plugins) {
                if (call.request.httpMethod == HttpMethod.Get ||
                    call.request.httpMethod == HttpMethod.Head
                ) {
                    call.response.cacheControl(CacheControl.MaxAge(300))
                }
            }

            intercept(ApplicationCallPipeline.Call) {
                call.respond(LocalFileContent(File(basedir, "plugins/StaticContentTest.kt")))
            }
        }

        client.get("/").let { response ->
            assertEquals(
                File(basedir, "plugins/StaticContentTest.kt".replaceSeparators()).readText(),
                response.bodyAsText()
            )
            assertEquals("max-age=300", response.headers[HttpHeaders.CacheControl])
        }
    }

    @Test
    @Suppress("DEPRECATION")
    fun testLegacyPriority() = testApplication {
        routing {
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

        client.get("/before").let { response ->
            assertEquals("before", response.bodyAsText())
        }

        client.get("/after").let { response ->
            assertEquals("after", response.bodyAsText())
        }
    }

    @Test
    @Suppress("DEPRECATION")
    fun testLegacyResourcesPreCompressedServeEncoded() = testApplication {
        routing {
            static {
                preCompressed {
                    resources()
                }
            }
        }

        client.get("/test-resource.txt") {
            header(HttpHeaders.AcceptEncoding, "br")
        }.let { response ->
            assertEquals("br", response.bodyAsText().trim())
            assertEquals(ContentType.Text.Plain, response.contentType()!!.withoutParameters())
            assertEquals("br", response.headers[HttpHeaders.ContentEncoding].orEmpty())
        }

        client.get("/test-resource.txt") {
            header(HttpHeaders.AcceptEncoding, "gzip")
        }.let { response ->
            assertEquals("gz", response.bodyAsText().trim())
            assertEquals(ContentType.Text.Plain, response.contentType()!!.withoutParameters())
            assertEquals("gzip", response.headers[HttpHeaders.ContentEncoding].orEmpty())
        }

        client.get("/test-resource.txt").let { response ->
            assertEquals("plain", response.bodyAsText().trim())
            assertEquals(ContentType.Text.Plain, response.contentType()!!.withoutParameters())
            assertNull(response.headers[HttpHeaders.ContentEncoding])
        }
    }

    @Test
    @Suppress("DEPRECATION")
    fun testLegacyPreCompressedSuppressCompressionIfAlreadyCompressed() = testApplication {
        install(Compression)

        routing {
            static {
                preCompressed {
                    resources()
                }
            }
        }

        client.get("/test-resource.txt") {
            header(HttpHeaders.AcceptEncoding, "gzip")
        }.let { response ->
            assertEquals("gz", response.bodyAsText().trim())
            assertEquals(ContentType.Text.Plain, response.contentType()!!.withoutParameters())
            assertEquals("gzip", response.headers[HttpHeaders.ContentEncoding].orEmpty())
        }
    }

    @Test
    @Suppress("DEPRECATION")
    fun testResourcesPreCompressedTypesOrder() = testApplication {
        routing {
            static("firstgz") {
                preCompressed(CompressedFileType.GZIP, CompressedFileType.BROTLI) {
                    resources()
                }
            }
            static("firstbr") {
                preCompressed(CompressedFileType.BROTLI, CompressedFileType.GZIP) {
                    resources()
                }
            }
        }

        client.get("/firstgz/test-resource.txt") {
            header(HttpHeaders.AcceptEncoding, "gzip, br")
        }.let { response ->
            assertEquals(ContentType.Text.Plain, response.contentType()!!.withoutParameters())
            assertEquals("gzip", response.headers[HttpHeaders.ContentEncoding].orEmpty())
        }

        client.get("/firstbr/test-resource.txt") {
            header(HttpHeaders.AcceptEncoding, "gzip, br")
        }.let { response ->
            assertEquals(ContentType.Text.Plain, response.contentType()!!.withoutParameters())
            assertEquals("br", response.headers[HttpHeaders.ContentEncoding].orEmpty())
        }
    }

    @Test
    fun testLocalPathContent() = testApplication {
        routing {
            get("path") {
                call.respond(
                    LocalPathContent(
                        Paths.get("jvm/test-resources/public/file.txt"),
                    )
                )
            }
            get("path-relative") {
                call.respond(
                    LocalPathContent(
                        Paths.get("jvm/test-resources"),
                        Paths.get("public/file.txt")
                    )
                )
            }
            get("zip") {
                val filePath = Paths.get("jvm/test-resources/public.zip")

                @Suppress("BlockingMethodInNonBlockingContext")
                val fileSystem = FileSystems.newFileSystem(filePath, StaticContentTest::class.java.classLoader)

                val path = fileSystem.getPath("public/nested/file-nested.txt")
                call.respond(LocalPathContent(path))
            }
        }

        val responsePath = client.get("path")
        assertEquals(ContentType.Text.Plain, responsePath.contentType()!!.withoutParameters())
        assertEquals("file.txt", responsePath.bodyAsText().trim())

        val responsePathRelative = client.get("path-relative")
        assertEquals(ContentType.Text.Plain, responsePathRelative.contentType()!!.withoutParameters())
        assertEquals("file.txt", responsePathRelative.bodyAsText().trim())

        val responseZip = client.get("zip")
        assertEquals(ContentType.Text.Plain, responseZip.contentType()!!.withoutParameters())
        assertEquals("file-nested.txt", responseZip.bodyAsText().trim())
    }

    @Test
    fun testCharset() = testApplication {
        val fileName = "file"
        val extensions = mapOf(
            "js" to ContentType.Text.JavaScript,
            "css" to ContentType.Text.CSS,
            "svg" to ContentType.Image.SVG,
            "xml" to ContentType.Application.Xml,
        )

        routing {
            staticFiles("staticFiles", File("jvm/test-resources/public/types"))
            staticFileSystem("staticFileSystem", "jvm/test-resources/public/types")
            staticResources("staticResources", "public/types")
        }

        suspend fun testCharset(pathPrefix: String) {
            extensions.forEach { (extension, contentType) ->
                client.get("$pathPrefix/$fileName.$extension").apply {
                    assertEquals(contentType.withCharset(Charsets.UTF_8), contentType())
                }
            }
        }

        testCharset("staticFiles")
        testCharset("staticFileSystem")
        testCharset("staticResources")
    }

    @Test
    fun testPathFromChangingZip() = testApplication {
        val stringPath = "jvm/test-resources/dynamic.zip"
        val path = Paths.get(stringPath)
        val firstFileName = "firstFile.txt"
        val secondFileName = "secondFile.txt"
        val firstContent = "Hello"
        val secondContent = "World"
        val firstZipFile = createZipFile(stringPath, firstFileName, firstContent)

        routing {
            staticZip(
                remotePath = "static",
                basePath = null,
                zip = path,
            )
        }

        val testWatchService = FileSystems.getDefault().newWatchService()
        path.parent.register(
            testWatchService,
            arrayOf(
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_DELETE,
                StandardWatchEventKinds.ENTRY_MODIFY,
                StandardWatchEventKinds.OVERFLOW
            ),
            SensitivityWatchEventModifier.HIGH
        )

        val firstResponse = client.get("static/$firstFileName")
        assertEquals(HttpStatusCode.OK, firstResponse.status)
        assertEquals(firstContent, firstResponse.bodyAsText())

        firstZipFile.delete()
        val secondZipFile = createZipFile(stringPath, secondFileName, secondContent)

        // Wait for the watch service to detect the change
        testWatchService.take()
        delay(3000)

        val secondResponse = client.get("static/$secondFileName")
        assertEquals(HttpStatusCode.OK, secondResponse.status)
        assertEquals(secondContent, secondResponse.bodyAsText())

        val firstNotFound = client.get("static/$firstFileName")
        assertEquals(HttpStatusCode.NotFound, firstNotFound.status)

        secondZipFile.delete()
    }

    private fun createZipFile(zipFileName: String, fileName: String, content: String): File {
        FileOutputStream(zipFileName).use { fos ->
            ZipOutputStream(fos).use { zos ->
                val zipEntry = ZipEntry(fileName)
                zos.putNextEntry(zipEntry)
                zos.write(content.toByteArray())
                zos.closeEntry()
            }
        }
        return File(zipFileName)
    }

    @Test
    fun `test custom ETag and LastModified with ConditionalHeaders`(@TempDir filesDir: File) = testApplication {
        val date = GMTDate()
        val etag = "etag"

        install(ConditionalHeaders)
        routing {
            staticResources("staticResources", "public") {
                configure(etag, date)
            }
            staticFiles("staticFiles", basedir) {
                configure(etag, date)
            }
            staticFileSystem("staticFileSystem", "jvm/test-resources/public") {
                configure(etag, date)
            }
            staticZip("staticZip", "public", Paths.get("jvm/test-resources/public.zip")) {
                configure(etag, date)
            }

            val file = File(filesDir, "file.txt")
            val brFile = File(filesDir, "file.txt.br")
            file.writeText("file.txt")
            brFile.writeText("temp.br")
            staticFiles("staticFilesPrecompressed", filesDir) {
                configure(etag, date)
            }
        }

        suspend fun ApplicationTestBuilder.testCustomEtagAndLastModified(
            url: String,
            expectedEtag: String,
            expectedDate: GMTDate,
            acceptEncoding: String? = null
        ) {
            val response = client.get(url) {
                headers {
                    acceptEncoding?.let { append(HttpHeaders.AcceptEncoding, acceptEncoding) }
                }
            }
            assertEquals(HttpStatusCode.OK, response.status)
            val etag = response.headers[HttpHeaders.ETag] ?: fail("no ETag")
            assertEquals(expectedEtag.quote(), etag)
            assertEquals(expectedDate.toHttpDate(), response.headers[HttpHeaders.LastModified])
        }

        testCustomEtagAndLastModified("staticFiles/plugins/PartialContentTest.kt", etag, date)
        testCustomEtagAndLastModified("staticResources/nested/file-nested.txt", etag, date)
        testCustomEtagAndLastModified("staticFileSystem/nested/file-nested.txt", etag, date)
        testCustomEtagAndLastModified("staticZip/nested/file-nested.txt", etag, date)

        // precompressed
        testCustomEtagAndLastModified("staticFilesPrecompressed/file.txt", etag, date, "br")
        testCustomEtagAndLastModified("staticResources/nested/file-nested.txt", etag, date, "br")
        testCustomEtagAndLastModified("staticFileSystem/nested/file-nested.txt", etag, date, "br")
        testCustomEtagAndLastModified("staticZip/nested/file-nested.txt", etag, date, "br")
    }

    @Test
    fun testResourcesSiblings() = testApplication {
        routing {
            staticResources("/remote", "public/nested")
            staticResources("/remote", "public")
        }

        val responseFileNested = client.get("/remote/file-nested.txt")
        assertEquals(HttpStatusCode.OK, responseFileNested.status)
        assertEquals("file-nested.txt", responseFileNested.bodyAsText().trim())

        val responseFile = client.get("/remote/file.txt")
        assertEquals(HttpStatusCode.OK, responseFile.status)
        assertEquals("file.txt", responseFile.bodyAsText().trim())
    }

    private fun StaticContentConfig<*>.configure(etag: String, date: GMTDate) {
        preCompressed(CompressedFileType.BROTLI)
        etag { EntityTagVersion(etag) }
        lastModified { date }
    }

    @Test
    fun `test strong etag`(@TempDir filesDir: File) = testApplication {
        File(filesDir, "test.txt").apply { writeText("test.txt") }
        File(filesDir, "test.txt.br").apply { writeText("test.txt.br") }
        File(filesDir, "test.txt.gz").apply { writeText("test.txt.gz") }

        install(ConditionalHeaders)
        routing {
            staticFiles("/static", filesDir) {
                preCompressed(CompressedFileType.BROTLI, CompressedFileType.GZIP)
                etag(ETagProvider.StrongSha256)
            }
        }

        val idResponse = client.get("/static/test.txt")
        assertEquals(HttpStatusCode.OK, idResponse.status)
        val idEtag = idResponse.headers[HttpHeaders.ETag] ?: error("missing ETag for identity")
        assertNull(idResponse.headers[HttpHeaders.ContentEncoding])

        val brResponse = client.get("/static/test.txt") {
            header(HttpHeaders.AcceptEncoding, "br")
        }
        assertEquals(HttpStatusCode.OK, brResponse.status)
        val brEtag = brResponse.headers[HttpHeaders.ETag] ?: error("missing ETag for br")
        assertEquals("br", brResponse.headers[HttpHeaders.ContentEncoding])

        val gzResponse = client.get("/static/test.txt") {
            header(HttpHeaders.AcceptEncoding, "gzip")
        }
        assertEquals(HttpStatusCode.OK, gzResponse.status)
        val gzEtag = gzResponse.headers[HttpHeaders.ETag] ?: error("missing ETag for gz")
        assertEquals("gzip", gzResponse.headers[HttpHeaders.ContentEncoding])

        assertNotEquals(idEtag, brEtag)
        assertNotEquals(idEtag, gzEtag)
        assertNotEquals(gzEtag, brEtag)
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
