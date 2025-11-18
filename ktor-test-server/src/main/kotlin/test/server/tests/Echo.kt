/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package test.server.tests

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.*

internal fun Application.echoTest() {
    routing {
        route("/echo") {
            route("/method") {
                handle {
                    val method = call.request.local.method.value
                    // Some methods don't allow body, so return result in a header
                    call.response.header("Http-Method", method)
                    call.response.status(HttpStatusCode.OK)
                }
            }

            route("/headers") {
                handle {
                    val headers = call.request.headers.entries().joinToString("\n") { (name, value) ->
                        "${name.lowercase()}: $value"
                    }
                    call.respondText(headers)
                }
            }

            post("/stream") {
                val inputChannel = call.receiveChannel()

                call.respondBytesWriter(status = HttpStatusCode.OK) {
                    val outputChannel = this
                    while (true) {
                        val inputLine = inputChannel.readUTF8Line() ?: break
                        outputChannel.writeStringUtf8("server: $inputLine\n")
                        outputChannel.flush()
                    }
                }
            }
        }
    }
}
