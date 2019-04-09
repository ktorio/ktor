package io.ktor.client.tests.utils

import ch.qos.logback.classic.*
import io.ktor.server.engine.*
import io.ktor.server.jetty.*
import org.slf4j.*

private val DEFAULT_PORT: Int = 8080

internal fun startServer(): ApplicationEngine {
    val logger = LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME) as ch.qos.logback.classic.Logger
    logger.level = Level.WARN

    return embeddedServer(Jetty, DEFAULT_PORT) {
        tests()
        benchmarks()
    }.start()
}

/**
 * Start server for tests.
 */
fun main() {
    startServer()
}
