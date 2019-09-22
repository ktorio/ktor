/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests.utils

import ch.qos.logback.classic.*
import io.ktor.client.tests.utils.tests.*
import io.ktor.server.engine.*
import io.ktor.server.jetty.*
import org.slf4j.*
import java.io.*
import java.util.concurrent.*

private const val DEFAULT_PORT: Int = 8080
private const val HTTP_PROXY_PORT: Int = 8082

internal fun startServer(): Closeable {
    val logger = LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME) as ch.qos.logback.classic.Logger
    logger.level = Level.WARN

    val server = embeddedServer(Jetty, DEFAULT_PORT) {
        tests()
        benchmarks()
    }.start()

    val proxyServer = TestTcpServer(HTTP_PROXY_PORT, ::proxyHandler)

    return Closeable {
        proxyServer.close()
        server.stop(0L, 0L, TimeUnit.MILLISECONDS)
    }
}

/**
 * Start server for tests.
 */
fun main() {
    startServer()
}
