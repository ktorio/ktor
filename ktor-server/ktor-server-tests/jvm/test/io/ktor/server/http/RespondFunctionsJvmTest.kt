/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.http

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import java.io.*
import java.nio.file.*
import kotlin.test.*

class RespondFunctionsJvmTest {
    @Test
    fun testRespondBytes(): Unit = withTestApplication {
        application.routing {
            get("/output-stream") {
                call.respondOutputStream(contentLength = 2) { write(1); write(2) }
            }
            get("/text-writer") {
                call.respondTextWriter(contentLength = 2) { write(1); write(2) }
            }
        }

        handleRequest(HttpMethod.Get, "/output-stream").let { call ->
            assertEquals("1, 2", call.response.byteContent?.joinToString())
            assertEquals("2", call.response.headers[HttpHeaders.ContentLength])
        }
        handleRequest(HttpMethod.Get, "/text-writer").let { call ->
            assertEquals("1, 2", call.response.byteContent?.joinToString())
            assertEquals("2", call.response.headers[HttpHeaders.ContentLength])
        }
    }

    @Test
    fun testRespondFile() = testApplication {
        val baseDirPath = "jvm/test-resources/"
        val filePath = "test-resource.txt"
        routing {
            get("/respondFile-File-String") {
                call.respondFile(File(baseDirPath), filePath)
            }
            get("/respondFile-File") {
                call.respondFile(File(baseDirPath + filePath))
            }
            get("/respondFile-Path-Path") {
                call.respondFile(Paths.get(baseDirPath), Paths.get(filePath))
            }
            get("/respondFile-Path") {
                call.respondFile(Paths.get(baseDirPath + filePath))
            }
        }

        val expected = "plain"
        val firstBody = client.get("/respondFile-File-String").trimmedBody()
        val secondBody = client.get("/respondFile-File").trimmedBody()
        val thirdBody = client.get("/respondFile-Path-Path").trimmedBody()
        val fourthBody = client.get("/respondFile-Path").trimmedBody()
        assertEquals(expected, firstBody)
        assertEquals(expected, secondBody)
        assertEquals(expected, thirdBody)
        assertEquals(expected, fourthBody)
    }

    private suspend fun HttpResponse.trimmedBody(): String {
        return bodyAsText().trim()
    }
}
