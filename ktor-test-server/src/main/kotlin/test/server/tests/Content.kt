/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package test.server.tests

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.util.*
import io.ktor.utils.io.*
import kotlinx.coroutines.delay
import test.server.fail
import test.server.makeString
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

internal fun Application.contentTestServer() {
    val activeStreams = ConcurrentHashMap.newKeySet<String>()

    routing {
        route("/content") {
            get("/uri") {
                call.respondText { call.request.local.uri }
            }

            get("/empty") {
                call.respond("")
            }
            get("/chunked-data") {
                call.respondTextWriter {
                    val text = "x".repeat(call.parameters["size"]?.toInt() ?: 1000)
                    text.chunked(255).forEach { write(it) }
                }
            }
            head("/emptyHead") {
                call.respond(
                    object : OutgoingContent.NoContent() {
                        override val contentLength: Long = 150
                    }
                )
            }
            get("/hello") {
                call.respond("hello")
            }
            get("/xxx") {
                call.respond(
                    buildString {
                        append("x".repeat(100))
                    }
                )
            }

            post("/pseudo-auth") {
                if (call.request.headers.contains("respond")) {
                    val content = call.request.receiveChannel().toByteArray()
                    call.respond(content.size.toString())
                } else {
                    call.respond(HttpStatusCode.Forbidden)
                }
            }
            post("/echo") {
                val content = call.request.receiveChannel().toByteArray()
                call.respond(content)
            }
            get("/news") {
                val form = call.request.queryParameters

                check("myuser" == form["user"]!!)
                check("10" == form["page"]!!)

                call.respond("100")
            }
            get("/big-plain-text") {
                call.respondText {
                    buildString {
                        for (i in 1..10_000)
                            appendLine("I will not introduce deadlocks.")
                    }
                }
            }
            post("/sign") {
                val form = call.receiveParameters()

                check("myuser" == form["user"]!!)
                check("abcdefg" == form["token"]!!)

                call.respond("success")
            }
            post("/upload") {
                val response = StringBuilder()
                call.receiveMultipart().forEachPart { part ->
                    check(part.contentDisposition?.disposition == "form-data")
                    response.append(part.makeString())
                    part.dispose()
                }

                call.respondText(response.toString())
            }
            put("/file-upload") {
                if (call.request.headers[HttpHeaders.ContentLength] == null) error("Content length is missing")

                val file = call.receiveMultipart().readPart() as? PartData.FileItem ?: call.fail("Invalid item")
                if (4 != file.headers[HttpHeaders.ContentLength]?.toInt()) call.fail("Size is missing")

                val value = file.provider().readInt()
                if (value != 42) call.fail("Invalid content")

                call.respond(HttpStatusCode.OK)
            }
            get("/stream") {
                val startDelay = call.parameters["startDelay"]?.toLong()?.milliseconds ?: Duration.ZERO
                val delay = call.parameters["delay"]?.toLong()?.milliseconds ?: Duration.ZERO
                val chunk = call.parameters["chunkSize"]?.toInt()?.let { ByteArray(it) }
                    ?: byteArrayOf(0, 0, 0, 42) // 42 (4-byte int)
                val chunkCount = call.parameters["chunkCount"]?.toInt()
                val id = call.parameters["id"]
                call.respond(
                    object : OutgoingContent.WriteChannelContent() {
                        override val contentType = ContentType.Application.OctetStream
                        override val contentLength: Long? = chunkCount?.let { it.toLong() * chunk.size }

                        override suspend fun writeTo(channel: ByteWriteChannel) {
                            if (id != null) activeStreams.add(id)
                            try {
                                delay(startDelay)
                                var remainingChunks = chunkCount
                                while (remainingChunks == null || remainingChunks > 0) {
                                    channel.writeFully(chunk)
                                    channel.flush()
                                    delay(delay)
                                    remainingChunks = remainingChunks?.minus(1)
                                }
                            } finally {
                                if (id != null) activeStreams.remove(id)
                            }
                        }
                    }
                )
            }

            get("/stream/active") {
                val id: String by call.parameters
                call.respondText(activeStreams.contains(id).toString())
            }

            get("/binary") {
                call.respondBytes(contentType = ContentType.Image.PNG) {
                    byteArrayOf((0x89).toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a)
                }
            }
        }
    }
}
