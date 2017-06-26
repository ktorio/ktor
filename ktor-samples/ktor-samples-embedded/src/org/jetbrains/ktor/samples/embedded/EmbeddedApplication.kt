package org.jetbrains.ktor.samples.embedded

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.host.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.jetty.*
import org.jetbrains.ktor.logging.*
import org.jetbrains.ktor.response.*
import org.jetbrains.ktor.routing.*

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
    embeddedServer(Jetty, 8080, watchPaths = listOf("ktor-samples-embedded"), module = Application::module).start()
}
