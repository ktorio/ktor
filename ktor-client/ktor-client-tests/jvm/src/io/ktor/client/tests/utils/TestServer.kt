package io.ktor.client.tests.utils

import ch.qos.logback.classic.*
import io.ktor.application.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.jetty.*
import org.slf4j.*
import org.slf4j.Logger

private val DEFAULT_PORT: Int = 8080

internal fun main(args: Array<String>) {
    val port = if (args.size > 1) args[1].toInt() else DEFAULT_PORT
    val logger = LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME) as ch.qos.logback.classic.Logger
    logger.level = Level.WARN

    embeddedServer(Jetty, port) {
        install(ShutDownUrl.ApplicationCallFeature) {
            shutDownUrl = "/shutdown"
        }

        routing {
            post("/echo") {
                val response = call.receiveText()
                call.respond(response)
            }
        }
    }.start(wait = true)
}
