/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests.utils.tests

import io.ktor.application.*
import io.ktor.client.tests.utils.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.util.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import kotlin.test.*

internal fun Application.contentTestServer() {
    routing {
        route("/content") {
            get("/empty") {
                call.respond("")
            }
            head("/emptyHead") {
                call.respond(object : OutgoingContent.NoContent() {
                    override val contentLength: Long = 150
                })
            }
            get("/hello") {
                call.respond("hello")
            }
            get("/xxx") {
                call.respond(buildString {
                    append("x".repeat(100))
                })
            }
            post("/echo") {
                val content = call.request.receiveChannel().toByteArray()
                call.respond(content)
            }
            get("/news") {
                val form = call.request.queryParameters

                assertEquals("myuser", form["user"]!!)
                assertEquals("10", form["page"]!!)

                call.respond("100")
            }
            post("/sign") {
                val form = call.receiveParameters()

                assertEquals("myuser", form["user"]!!)
                assertEquals("abcdefg", form["token"]!!)

                call.respond("success")
            }
            post("/upload") {
                val parts = call.receiveMultipart().readAllParts()
                parts.forEach { part ->
                    assertEquals(part.contentDisposition?.disposition, "form-data")
                }

                call.respondText(parts.makeString())
            }
            put("/file-upload") {
                val parts = call.receiveMultipart().readAllParts()
                if (call.request.headers[HttpHeaders.ContentLength] == null) error("Content length is missing")

                if (parts.size != 1) call.fail("Invalid form size: $parts")

                val file = parts.first() as? PartData.FileItem ?: call.fail("Invalid item")

                if (4 != file.headers[HttpHeaders.ContentLength]?.toInt()) call.fail("Size is missing")

                val value = file.provider().readInt()
                if (value != 42) call.fail("Invalid content")

                call.respond(HttpStatusCode.OK)
            }
            get("/stream") {
                call.respond(object : OutgoingContent.WriteChannelContent() {
                    override suspend fun writeTo(channel: ByteWriteChannel) {
                        while (true) {
                            channel.writeInt(42)
                        }
                    }
                })
            }
        }
    }
}
