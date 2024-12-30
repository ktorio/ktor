/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package test.server.tests

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.delay

private val content: String = buildString {
    repeat(1875) {
        append("X")
    }
}

internal fun Application.downloadTest() {
    routing {
        route("download") {
            get {
                val size = call.request.queryParameters["size"]?.toInt()

                if (size == null) {
                    call.respond(HttpStatusCode.BadRequest)
                    return@get
                }

                call.respond(HttpStatusCode.OK, ByteArray(size) { 0 })
            }
            get("8175") {
                call.respond(TextContent(content, ContentType.Text.Plain))
            }
            get("infinite") {
                call.respondOutputStream {
                    while (true) {
                        write("test".toByteArray())
                        delay(1)
                    }
                }
            }
        }
    }
}
