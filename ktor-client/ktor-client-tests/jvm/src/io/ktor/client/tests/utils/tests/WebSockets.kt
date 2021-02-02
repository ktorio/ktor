package io.ktor.client.tests.utils.tests

import io.ktor.application.*
import io.ktor.http.cio.websocket.*
import io.ktor.routing.*
import io.ktor.websocket.*

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
