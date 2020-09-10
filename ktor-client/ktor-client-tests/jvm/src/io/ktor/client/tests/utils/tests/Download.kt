/*
 * Copyright 2014-2020 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
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
            get("8175") {
                call.respond(TextContent(content, ContentType.Text.Plain))
            }
            get("infinite") {
                call.respondOutputStream {
                    while (true) {
                        write(1)
                        delay(1)
                    }
                }
            }
        }
    }
}
