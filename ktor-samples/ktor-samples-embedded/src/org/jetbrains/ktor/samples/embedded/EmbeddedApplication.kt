package org.jetbrains.ktor.samples.embedded

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.host.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.jetty.*
import org.jetbrains.ktor.routing.*
import java.util.concurrent.*

fun main(args: Array<String>) {
    var server: ApplicationHost? = null

    // NOTE: Change to embeddedJettyServer to use Jetty
    server = embeddedJettyServer(8080) {
        get("/") {
            response.sendText(ContentType.Text.Html, """Hello, world<br><a href="/bye">Say bye?</a>""")
        }

        get("/bye") {
            // Schedule server shutdown in 100ms
            val executor = Executors.newSingleThreadScheduledExecutor()
            executor.schedule({
                server?.stop()
                executor.shutdown()
            }, 100, TimeUnit.MILLISECONDS)

            response.sendText("Goodbye World!")
        }
    }

    server.start()
}
