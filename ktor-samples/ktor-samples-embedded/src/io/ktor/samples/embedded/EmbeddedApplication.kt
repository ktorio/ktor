package io.ktor.samples.embedded

import io.ktor.application.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.jetty.*

fun Application.module() {
    install(CallLogging)
    install(Routing) {
        get("/") {
            call.respondText("""Hello, world!<br><a href="/bye">Say bye?</a>""", ContentType.Text.Html)
        }
        get("/bye") {
            call.respondText("""Good bye!""", ContentType.Text.Html)
        }
    }
}

fun main(args: Array<String>) {
    embeddedServer(Jetty, commandLineEnvironment(args)).start()
}
