package io.ktor.client.tests

import io.ktor.application.*
import io.ktor.client.engine.*
import io.ktor.client.request.*
import io.ktor.client.tests.utils.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.jetty.*
import kotlin.test.*

open class FeaturesTest(private val factory: HttpClientEngineFactory<*>) : TestWithKtor() {
    override val server: ApplicationEngine = embeddedServer(Jetty, serverPort) {
        routing {
            get("/body") {
                val size = call.parameters["size"]!!.toInt()
                val text = "x".repeat(size)
                call.respondText(text)
            }
        }
    }

    @Test
    fun ignoreBodyTest() {
        clientTest(factory) {
            test { client ->
                listOf(0, 1, 1024, 4 * 1024, 16 * 1024, 16 * 1024 * 1024).forEach {
                    client.get<Unit>(path = "/body", port = serverPort) {
                        parameter("size", it.toString())
                    }
                }
            }
        }

        clientTest(factory.config {
            pipelining = false
        }) {
            test { client ->
                listOf(0, 1, 1024, 4 * 1024, 16 * 1024, 16 * 1024 * 1024).forEach {
                    client.get<Unit>(path = "/body", port = serverPort) {
                        parameter("size", it.toString())
                    }
                }
            }
        }
    }
}