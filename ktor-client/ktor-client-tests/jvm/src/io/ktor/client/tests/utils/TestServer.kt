package io.ktor.client.tests.utils

import ch.qos.logback.classic.*
import io.ktor.application.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.jetty.*
import org.slf4j.*
import org.slf4j.Logger

private val DEFAULT_PORT: Int = 8080

internal fun startServer(): ApplicationEngine {
    val logger = LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME) as ch.qos.logback.classic.Logger
    logger.level = Level.WARN

    return embeddedServer(Jetty, DEFAULT_PORT) {
        routing {
            post("/echo") {
                val response = call.receiveText()
                call.respond(response)
            }
            get("/bytes") {
                val size = call.request.queryParameters["size"]!!.toInt()
                call.respondBytes(makeArray(size))
            }
            route("/json") {
                get("/users") {
                    call.respondText("[{'id': 42, 'login': 'TestLogin'}]", contentType = ContentType.Application.Json)
                }
                get("/photos") {
                    call.respondText("[{'id': 4242, 'path': 'cat.jpg'}]", contentType = ContentType.Application.Json)
                }
            }
            route("/compression") {
                route("/deflate") {
                    install(Compression) { deflate() }
                    setCompressionEndpoints()
                }
                route("/gzip") {
                    install(Compression) { gzip() }
                    setCompressionEndpoints()
                }
                route("/identity") {
                    install(Compression) { identity() }
                    setCompressionEndpoints()
                }
            }
        }
    }.start()
}

private fun Route.setCompressionEndpoints() {
    get {
        call.respondText("Compressed response!")
    }
}

/**
 * Start server for tests.
 */
fun main() {
    startServer()
}
