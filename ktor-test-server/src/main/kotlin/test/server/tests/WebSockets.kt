/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package test.server.tests

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.util.*
import io.ktor.websocket.*
import kotlinx.serialization.json.Json

internal fun Application.webSockets() {
    routing {
        route("websockets") {
            webSocket("echo") {
                for (frame in incoming) {
                    when (frame) {
                        is Frame.Text -> {
                            val text = frame.readText()
                            send(Frame.Text(text))
                        }

                        is Frame.Close -> close()
                        is Frame.Binary -> send(Frame.Binary(fin = true, frame.data))
                        else -> error("Unsupported frame type: ${frame.frameType}.")
                    }
                }
            }
            webSocket("receive-backpressure") {
                for (i in 0..1000) {
                    send(Frame.Text("Hello $i"))
                }
            }
            webSocket("headers") {
                val headers = call.request.headers.toMap()
                val headersJson = Json.encodeToString(headers)
                send(Frame.Text(headersJson))
            }
            webSocket("close") {
                for (packet in incoming) {
                    val data = packet.data
                    if (String(data) == "End") {
                        close(CloseReason(1000, "End"))
                    }
                }
            }
            webSocket("echo-query") {
                val param = call.parameters["param"] ?: error("No param provided")
                send(Frame.Text(param))
            }
            webSocketRaw("count-pong") {
                send(Frame.Ping("ping".toByteArray()))

                var countPong = 0
                for (frame in incoming) {
                    when (frame) {
                        is Frame.Pong -> countPong++
                        is Frame.Text -> send(Frame.Text("$countPong"))
                        else -> error("Unsupported frame type: ${frame.frameType}.")
                    }
                }
            }
            webSocketRaw("fragmented-echo") {
                val fragmentSize = call.request.queryParameters["fragmentSize"]?.toIntOrNull()
                    ?: error("No fragmentSize provided")
                var message = ByteArray(0)
                for (frame in incoming) {
                    when (frame) {
                        is Frame.Text -> {
                            message += frame.data
                            if (!frame.fin) continue
                            val chunks = message.toList().chunked(fragmentSize)
                            chunks.forEachIndexed { index, chunk ->
                                send(Frame.Text(fin = index == chunks.lastIndex, chunk.toByteArray()))
                            }
                            message = ByteArray(0)
                        }

                        is Frame.Close -> return@webSocketRaw
                        else -> error("Unsupported frame type: ${frame.frameType}.")
                    }
                }
            }
            webSocketRaw("endless-message") {
                // Never sends the final fragment, so the message can't be received in full.
                // Flushing applies backpressure: the outgoing channel is unlimited, and `send` alone never suspends.
                val fragment = "x".repeat(4).toByteArray()
                repeat(16 * 1024) {
                    send(Frame.Text(fin = false, fragment))
                    flush()
                }
                // Keep the message unfinished until the client closes the connection
                for (frame in incoming) {
                    if (frame is Frame.Close) break
                }
            }
            webSocket("sub-protocol", protocol = "test-protocol") {
                for (frame in incoming) {
                    when (frame) {
                        is Frame.Text -> {
                            val text = frame.readText()
                            send(Frame.Text(text))
                        }

                        is Frame.Binary -> send(Frame.Binary(fin = true, frame.data))
                        else -> error("Unsupported frame type: ${frame.frameType}.")
                    }
                }
            }
            webSocket("text") {
                val size = call.request.queryParameters["size"]?.toIntOrNull() ?: error("No size provided")
                val payload = "x".repeat(size)
                send(Frame.Text(payload))
            }
            get("500") {
                call.respond(HttpStatusCode.InternalServerError)
            }
        }
    }
}
