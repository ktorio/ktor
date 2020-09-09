package io.ktor.client.tests.utils.tests

import io.ktor.application.*
import io.ktor.http.cio.websocket.*
import io.ktor.routing.*
import io.ktor.websocket.*

public fun Application.webSockets() {
    routing {
        route("websockets") {
            webSocket("echo") {
                for (packet in incoming) {
                    val data = packet.data
                    send(Frame.Text(fin = true, data = data))
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
