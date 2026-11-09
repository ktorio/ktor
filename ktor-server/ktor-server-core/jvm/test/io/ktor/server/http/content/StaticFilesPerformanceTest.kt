/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.http.content

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import io.ktor.utils.io.*
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.io.files.Path
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.measureTime

@OptIn(ExperimentalKtorApi::class)
class StaticFilesPerformanceTest {

    @Test
    fun `profile kotlinx io staticFiles`() = testApplication {
        val directory = createTempDirectory("ktor-static-files-performance").toFile()
        try {
            createFiles(directory)
            routing { staticFiles("/static", Path(directory.path)) }
            startApplication()

            val duration = measureTime {
                repeat(REQUEST_COUNT) { requestIndex ->
                    val response = client.get("/static/file${requestIndex % FILE_COUNT}.txt") {
                        header(HttpHeaders.AcceptEncoding, "gzip")
                    }
                    assertEquals(HttpStatusCode.OK, response.status)
                    response.bodyAsText()
                }
            }
            println("kotlinx.io staticFiles: $REQUEST_COUNT requests in $duration")
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun `profile java io staticFiles`() = testApplication {
        val directory = createTempDirectory("ktor-static-files-performance").toFile()
        try {
            createFiles(directory)
            routing {
                preCompressed(CompressedFileType.GZIP) {
                    staticFiles("/static", directory)
                }
            }
            startApplication()

            val duration = measureTime {
                repeat(REQUEST_COUNT) { requestIndex ->
                    val response = client.get("/static/file${requestIndex % FILE_COUNT}.txt") {
                        header(HttpHeaders.AcceptEncoding, "gzip")
                    }
                    assertEquals(HttpStatusCode.OK, response.status)
                    response.bodyAsText()
                }
            }
            println("java.io staticFiles: $REQUEST_COUNT requests in $duration")
        } finally {
            directory.deleteRecursively()
        }
    }

    private fun createFiles(directory: File) {
        repeat(FILE_COUNT) { fileIndex ->
            File(directory, "file$fileIndex.txt")
                .writeText("original $fileIndex".repeat(1000))
            File(directory, "file$fileIndex.txt.gz")
                .writeText("compressed $fileIndex".repeat(800))
        }
    }

    private companion object {
        const val FILE_COUNT = 3
        const val REQUEST_COUNT = 100_000
    }
}
