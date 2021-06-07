/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.tests.utils.tests

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.response.*
import io.ktor.routing.*
import kotlinx.coroutines.*

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
