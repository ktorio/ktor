package org.jetbrains.ktor.samples.embedded

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.host.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.jetty.*
import org.jetbrains.ktor.response.*
import org.jetbrains.ktor.routing.*
import java.util.concurrent.*

fun main(args: Array<String>) {
    var server: JettyApplicationHost? = null

    // NOTE: Change to embeddedJettyServer to use Jetty
    server = embeddedServer(Jetty, 8080) {
        install(Routing) {
            get("/") {
                call.respondText("""Hello, world<br><a href="/bye">Say bye?</a>""", ContentType.Text.Html)
            }

            get("/bye") {
                // Schedule server shutdown in 100ms
                val executor = Executors.newSingleThreadScheduledExecutor()
                executor.schedule({
                    server?.stop(100, 1000, TimeUnit.MILLISECONDS)
                    executor.shutdown()
                }, 100, TimeUnit.MILLISECONDS)

                call.respondText("Goodbye World!")
            }
        }
    }

    server.start()
}
