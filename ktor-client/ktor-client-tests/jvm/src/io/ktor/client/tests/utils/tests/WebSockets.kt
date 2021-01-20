package io.ktor.client.tests.utils.tests

import io.ktor.application.*
import io.ktor.http.cio.websocket.*
import io.ktor.routing.*
import io.ktor.util.*
import io.ktor.websocket.*
import kotlinx.serialization.*
import kotlinx.serialization.json.*

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
                        is Frame.Binary -> send(Frame.Binary(fin = true, frame.data))
                        else -> error("Unsupported frame type: ${frame.frameType}.")
                    }
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
        }
    }
}
