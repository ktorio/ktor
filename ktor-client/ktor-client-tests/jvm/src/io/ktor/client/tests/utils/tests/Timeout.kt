/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests.utils.tests

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*

internal fun Application.timeoutTest() {
    routing {
        route("/timeout") {
            head("/with-delay") {
                val delay = call.parameters["delay"]!!.toLong()
                delay(delay)
                call.respond(HttpStatusCode.OK)
            }

            get("/with-delay") {
                val delay = call.parameters["delay"]!!.toLong()
                delay(delay)
                call.respondText { "Text" }
            }

            get("/with-stream") {
                val delay = call.parameters["delay"]!!.toLong()
                val response = "Text".toByteArray()
                call.respond(object : OutgoingContent.WriteChannelContent() {
                    override val contentType = ContentType.Application.OctetStream
                    override suspend fun writeTo(channel: ByteWriteChannel) {
                        for (offset in response.indices) {
                            channel.writeFully(response, offset, 1)
                            channel.flush()
                            delay(delay)
                        }
                    }
                })
            }

            get("/with-redirect") {
                val delay = call.parameters["delay"]!!.toLong()
                val count = call.parameters["count"]!!.toInt()
                val url = if (count == 0) "/timeout/with-delay?delay=$delay"
                else "/timeout/with-redirect?delay=$delay&count=${count - 1}"
                delay(delay)
                call.respondRedirect(url)
            }

            post("/slow-read") {
                val buffer = ByteArray(1024 * 1024)
                val input = call.request.receiveChannel()
                var count = 0
                while (true) {
                    val read = input.readAvailable(buffer)
                    if (read == -1) break
                    count += read
                    if (count >= 1024 * 1024) {
                        count = 0
                        delay(1000)
                    }
                }

                call.respond(HttpStatusCode.OK)
            }
        }
    }
}
