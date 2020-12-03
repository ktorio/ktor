/*
 * Copyright 2014-2020 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests.utils

import ch.qos.logback.classic.*
import io.ktor.application.*
import io.ktor.client.tests.utils.tests.*
import io.ktor.http.*
import io.ktor.network.tls.certificates.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.jetty.*
import org.slf4j.*
import java.io.*
import java.util.concurrent.*

private const val DEFAULT_PORT: Int = 8080
private const val DEFAULT_TLS_PORT: Int = 8089
private const val HTTP_PROXY_PORT: Int = 8082

internal fun startServer(): Closeable {
    val logger = LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME) as ch.qos.logback.classic.Logger
    logger.level = Level.WARN

    val tcpServer = TestTcpServer(HTTP_PROXY_PORT, ::tcpServerHandler)

    val server = embeddedServer(CIO, DEFAULT_PORT) {
        tests()
        benchmarks()
    }.start()

    val tlsServer = setupTLSServer()
    tlsServer.start()

    Thread.sleep(1000)

    return Closeable {
        tcpServer.close()
        server.stop(0L, 0L, TimeUnit.MILLISECONDS)
        tlsServer.stop(0L, 0L, TimeUnit.MILLISECONDS)
    }
}

/**
 * Start server for tests.
 */
public fun main() {
    val handler = startServer()
    try {
        while (!Thread.interrupted()) {
        }
    } finally {
        handler.close()
    }
}

private fun setupTLSServer(): ApplicationEngine {
    val file = File("build/client-tls-test-server.jks")
    val testKeyStore = generateCertificate(file)
    val tlsServer = embeddedServer(
        Jetty,
        applicationEngineEnvironment {
            sslConnector(
                testKeyStore,
                "mykey",
                { "changeit".toCharArray() },
                { "changeit".toCharArray() },
                {
                    this.port = DEFAULT_TLS_PORT
                    this.keyStorePath = file
                }
            )

            module {
                tlsTests()
                cors()
                routing {
                    route("/cookies/httponly") {
                        val cookie = Cookie(
                            "One",
                            "value1",
                            httpOnly = true,
                            extensions = mapOf("samesite" to "none"),
                            secure = true
                        )
                        get {
                            with(call.response.cookies) {
                                append(cookie)
                            }
                            call.respondText { "Cookie set" }
                        }
                        get("/test") {
                            if (cookie.value == call.request.cookies[cookie.name]) {
                                call.respondText { "Cookie matched" }
                            } else {
                                call.respondText(status = HttpStatusCode.ExpectationFailed) {
                                    when (val value = call.request.cookies[cookie.name]) {
                                        null -> "Cookie ${cookie.name} not found"
                                        else -> "Cookie value $value does not match ${cookie.value}"
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    )

    return tlsServer
}
