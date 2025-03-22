/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package test.server.tests

import io.ktor.client.request.forms.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.*

private const val TEST_FILE_SIZE = 1024 * 1024

internal fun Application.multiPartFormDataTest() {
    routing {
        route("multipart") {
            post {
                call.receiveMultipart(formFieldLimit = TEST_FILE_SIZE + 1L).forEachPart {
                    try {
                        if (it is PartData.FileItem) {
                            val array = ByteArray(TEST_FILE_SIZE)
                            it.provider().readFully(array)
                        }
                    } finally {
                        it.dispose()
                    }
                }
                call.respond(HttpStatusCode.OK)
            }
            post("empty") {
                call.receiveMultipart().readPart()
                call.respond(HttpStatusCode.OK)
            }
            post("receive") {
                val multipart = MultiPartFormDataContent(
                    formData {
                        append("text", "Hello, World!")
                        append("file", ByteArray(1024) { it.toByte() }, Headers.build {
                            append(HttpHeaders.ContentDisposition, """form-data; name="file"; filename="test.bin"""")
                            append(HttpHeaders.ContentType, ContentType.Application.OctetStream.toString())
                        })
                    }
                )
                call.respond(multipart)
            }
        }
    }
}
