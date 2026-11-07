/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.http.content

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import io.ktor.utils.io.ExperimentalKtorApi
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemTemporaryDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalKtorApi::class)
class StaticFilesTest {

    @Test
    fun `serves file contents and content type`() = testApplication {
        withTemporaryDirectory { root ->
            writeFile(Path(root, "file.txt"), "file contents")
            routing { staticFiles("/static", root) }

            val response = client.get("/static/file.txt")

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("file contents", response.bodyAsText())
            assertEquals(
                ContentType.Text.Plain.toString(),
                response.headers[HttpHeaders.ContentType]?.substringBefore(';')
            )
        }
    }

    @Test
    fun `serves nested file`() = testApplication {
        withTemporaryDirectory { root ->
            val nested = Path(root, "nested")
            SystemFileSystem.createDirectories(nested)
            writeFile(Path(nested, "file.txt"), "nested contents")
            routing { staticFiles("/assets", root) }

            val response = client.get("/assets/nested/file.txt")

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("nested contents", response.bodyAsText())
        }
    }

    @Test
    fun `serves smallest accepted pre-compressed file`() = testApplication {
        withTemporaryDirectory { root ->
            writeFile(Path(root, "file.txt"), "original contents")
            writeFile(Path(root, "file.txt.gz"), "gzip")
            writeFile(Path(root, "file.txt.br"), "br")
            routing { staticFiles("/static", root) }

            val response = client.get("/static/file.txt") {
                header(HttpHeaders.AcceptEncoding, "gzip, br")
            }

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("br", response.bodyAsText())
            assertEquals("br", response.headers[HttpHeaders.ContentEncoding])
            assertEquals(HttpHeaders.AcceptEncoding, response.headers[HttpHeaders.Vary])
        }
    }

    @Test
    fun `serves next accepted pre-compressed file`() = testApplication {
        withTemporaryDirectory { root ->
            writeFile(Path(root, "file.txt"), "original contents")
            writeFile(Path(root, "file.txt.gz"), "gzip")
            writeFile(Path(root, "file.txt.br"), "br")
            routing { staticFiles("/static", root) }

            val response = client.get("/static/file.txt") {
                header(HttpHeaders.AcceptEncoding, "gzip")
            }

            assertEquals("gzip", response.bodyAsText())
            assertEquals("gzip", response.headers[HttpHeaders.ContentEncoding])
        }
    }

    @Test
    fun `serves original when no pre-compressed encoding is accepted`() = testApplication {
        withTemporaryDirectory { root ->
            writeFile(Path(root, "file.txt"), "original contents")
            writeFile(Path(root, "file.txt.gz"), "gzip")
            routing { staticFiles("/static", root) }

            val response = client.get("/static/file.txt") {
                header(HttpHeaders.AcceptEncoding, "identity")
            }

            assertEquals("original contents", response.bodyAsText())
            assertEquals(null, response.headers[HttpHeaders.ContentEncoding])
            assertEquals(HttpHeaders.AcceptEncoding, response.headers[HttpHeaders.Vary])
        }
    }

    @Test
    fun `serves original when accepted variant is missing for the requested file`() = testApplication {
        withTemporaryDirectory { root ->
            writeFile(Path(root, "file.txt"), "original contents")
            writeFile(Path(root, "other.txt.br"), "br")
            routing { staticFiles("/static", root) }

            val response = client.get("/static/file.txt") {
                header(HttpHeaders.AcceptEncoding, "br")
            }

            assertEquals("original contents", response.bodyAsText())
            assertEquals(null, response.headers[HttpHeaders.ContentEncoding])
        }
    }

    @Test
    fun `does not serve pre-compressed files added after route declaration`() = testApplication {
        withTemporaryDirectory { root ->
            writeFile(Path(root, "file.txt"), "original contents")
            routing { staticFiles("/static", root) }
            startApplication()
            writeFile(Path(root, "file.txt.gz"), "gzip")

            val response = client.get("/static/file.txt") {
                header(HttpHeaders.AcceptEncoding, "gzip")
            }

            assertEquals("original contents", response.bodyAsText())
            assertEquals(null, response.headers[HttpHeaders.ContentEncoding])
        }
    }

    @Test
    fun `missing file returns not found`() = testApplication {
        withTemporaryDirectory { root ->
            routing { staticFiles("/static", root) }

            val response = client.get("/static/missing.txt")

            assertEquals(HttpStatusCode.NotFound, response.status)
        }
    }

    @Test
    fun `directory returns not found`() = testApplication {
        withTemporaryDirectory { root ->
            SystemFileSystem.createDirectories(Path(root, "directory"))
            routing { staticFiles("/static", root) }

            val response = client.get("/static/directory")

            assertEquals(HttpStatusCode.NotFound, response.status)
        }
    }

    @Test
    fun `parent path cannot escape local directory`() = testApplication {
        withTemporaryDirectory { root ->
            val localPath = Path(root, "public")
            SystemFileSystem.createDirectories(localPath)
            writeFile(Path(root, "outside.txt"), "outside contents")
            routing { staticFiles("/static", localPath) }

            val response = client.get("/static/%2E%2E/outside.txt")

            assertEquals(HttpStatusCode.NotFound, response.status)
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    private fun temporaryPath(): Path = Path(SystemTemporaryDirectory, "ktor-static-files-${Uuid.random()}")

    private suspend fun withTemporaryDirectory(block: suspend (Path) -> Unit) {
        val path = temporaryPath()
        SystemFileSystem.createDirectories(path)
        try {
            block(path)
        } finally {
            deleteRecursively(path)
        }
    }

    private fun deleteRecursively(path: Path) {
        runCatching { SystemFileSystem.list(path) }.getOrNull()?.forEach(::deleteRecursively)
        SystemFileSystem.delete(path, mustExist = false)
    }

    private fun writeFile(path: Path, content: String) {
        SystemFileSystem.sink(path).buffered().use { it.write(content.encodeToByteArray()) }
    }
}
