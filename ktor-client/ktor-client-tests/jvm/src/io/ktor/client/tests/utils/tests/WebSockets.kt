package io.ktor.client.tests.utils.tests

import io.ktor.application.*
import io.ktor.http.cio.websocket.*
import io.ktor.routing.*
import io.ktor.websocket.*

fun Application.webSockets() {
    routing {
        route("websockets") {
            webSocket("echo") {
                for (packet in incoming) {
                    val data = packet.data
                    send(Frame.Text(fin = true, data = data))
                }
            }
        }
    }
}
