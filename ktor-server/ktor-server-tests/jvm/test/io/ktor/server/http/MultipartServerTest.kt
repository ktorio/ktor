/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.http

import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.ktor.util.cio.*
import io.ktor.utils.io.*
import io.ktor.utils.io.charsets.*
import kotlinx.coroutines.*
import kotlinx.io.*
import kotlin.test.*
import kotlin.time.*
import kotlin.time.Duration.Companion.seconds

private const val TEST_FILE_SIZE = 1_000_000

@Ignore // TODO KTOR-7505 Only works in JVM
class MultipartServerTest {

    private val fileDispositionHeaders = Headers.build {
        append(
            HttpHeaders.ContentDisposition,
            """form-data; name="file"; filename="test.png""""
        )
    }

    @Test
    fun testBigBoiFileNoContentLength() = testApplication {
        routing {
            post("/multipart") {
                call.receiveMultipart(formFieldLimit = TEST_FILE_SIZE + 1L).forEachPart {
                    try {
                        ByteChannel().use {
                            if (it is PartData.FileItem) {
                                it.provider().copyTo(this)
                            }
                        }
                    } finally {
                        it.dispose()
                    }
                }
                call.respond(HttpStatusCode.OK)
            }
        }

        val partData = InputProvider(size = null) {
            Buffer().apply {
                for (i in 0 until TEST_FILE_SIZE) {
                    writeByte(i.toByte())
                }
            }
        }
        val timeTaken = measureTime {
            client.preparePost("multipart") {
                setBody(
                    MultiPartFormDataContent(
                        formData {
                            append("file", partData, fileDispositionHeaders)
                        }
                    )
                )
            }.execute().let { result ->
                assertEquals(HttpStatusCode.OK, result.status)
            }
        }

        assertTrue(
            timeTaken < 5.seconds,
            "Time to upload big file: $timeTaken"
        )
    }

    @Test
    fun multiplePartsDoNotBlock() = testApplication {
        routing {
            post {
                val multipart = call.receiveMultipart(10 * 1024 * 1024)
                var part = multipart.readPart()
                while (part != null) {
                    part = multipart.readPart()
                }
            }
        }

        val bytes = ByteArray(5 * 1024 * 1024)

        withTimeout(5.seconds) {
            client.post("/") {
                setBody(
                    MultiPartFormDataContent(
                        formData {
                            append(
                                "payload",
                                "a".repeat(1000),
                                Headers.build {
                                    append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                                }
                            )
                            val provider = ChannelProvider(bytes.size.toLong()) {
                                ByteReadChannel(bytes)
                            }
                            append(
                                "file",
                                provider,
                                Headers.build {
                                    append(
                                        HttpHeaders.ContentType,
                                        ContentType.Application.OctetStream.toString()
                                    )
                                    append(HttpHeaders.ContentDisposition, "filename=some.txt")
                                }
                            )
                        }
                    )
                )
            }
        }
    }

    @Test
    fun withCustomBoundary() = testApplication {
        routing {
            post("/multipart/echo") {
                call.respondBytesWriter {
                    call.receiveMultipart().forEachPart {
                        writeString((it.name ?: "part") + ": ")
                        when (it) {
                            is PartData.FileItem ->
                                it.provider().copyTo(this)

                            is PartData.BinaryChannelItem ->
                                it.provider().copyTo(this)

                            is PartData.BinaryItem ->
                                writeBuffer(it.provider())

                            is PartData.FormItem ->
                                writeString(it.value)
                        }
                        writeByte('\n'.code.toByte())
                    }
                }
            }
        }

        client.post("multipart/echo") {
            val contentType =
                ContentType.MultiPart.FormData
                    .withParameter("boundary", "***bbb***")
                    .withCharset(Charsets.ISO_8859_1)

            setBody(
                TextContent(
                    buildString {
                        append("--***bbb***\r\n")
                        append("Content-Disposition: form-data; name=\"a story\"\r\n")
                        append("\r\n")
                        append(
                            "Hi user. The snake you gave me for free ate all the birds. " +
                                "Please take it back ASAP.\r\n"
                        )
                        append("--***bbb***\r\n")
                        append("Content-Disposition: form-data; name=\"attachment\"; filename=\"original.txt\"\r\n")
                        append("Content-Type: text/plain\r\n")
                        append("\r\n")
                        append("File content goes here\r\n")
                        append("--***bbb***--\r\n")
                    },
                    contentType = contentType
                )
            )
        }.let { response ->
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(
                """
                a story: Hi user. The snake you gave me for free ate all the birds. Please take it back ASAP.
                attachment: File content goes here
                """.trimIndent(),
                response.bodyAsText().trim()
            )
        }
    }
}
