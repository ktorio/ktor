package io.ktor.samples.embedded

import io.ktor.application.*
import io.ktor.features.*
import io.ktor.host.*
import io.ktor.http.*
import io.ktor.jetty.*
import io.ktor.pipeline.*
import io.ktor.response.*
import io.ktor.routing.*

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
