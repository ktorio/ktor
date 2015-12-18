package org.jetbrains.ktor.samples.embedded

import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.netty.*
import org.jetbrains.ktor.routing.*

fun main(args: Array<String>) {
    // NOTE: Change to embeddedJettyServer to use Jetty
    embeddedNettyServer(port = 8080) {
        get("/") {
            response.sendText("Hello")
        }
        get("/bye") {
            response.sendText("Goodbye World!")
        }
    }
}
