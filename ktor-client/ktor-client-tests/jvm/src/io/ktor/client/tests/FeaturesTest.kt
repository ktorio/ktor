package io.ktor.client.tests

import io.ktor.application.*
import io.ktor.client.call.*
import io.ktor.client.engine.*
import io.ktor.client.features.observer.*
import io.ktor.client.request.*
import io.ktor.client.response.*
import io.ktor.client.tests.utils.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.jetty.*
import kotlin.test.*

@Suppress("KDocMissingDocumentation")
abstract class FeaturesTest(private val factory: HttpClientEngineFactory<*>) : TestWithKtor() {
    override val server: ApplicationEngine = embeddedServer(Jetty, serverPort) {
        routing {
            get("/body") {
                val size = call.parameters["size"]!!.toInt()
                val text = "x".repeat(size)
                call.respondText(text)
            }
            get("/echo") {
                call.respondText("Hello, world")
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

        clientTest(factory) {

            config {
                engine {
                    pipelining = false
                }
            }

            test { client ->
                listOf(0, 1, 1024, 4 * 1024, 16 * 1024, 16 * 1024 * 1024).forEach {
                    client.get<Unit>(path = "/body", port = serverPort) {
                        parameter("size", it.toString())
                    }
                }
            }
        }
    }

    @Test
    fun bodyObserverTest() {
        var observerExecuted = false
        clientTest(factory) {

            val body = "Hello, world"
            config {
                ResponseObserver { response ->
                    val text = response.receive<String>()
                    assertEquals(body, text)
                    observerExecuted = true
                }
            }

            test { client ->
                val response = client.get<HttpResponse>(path = "/echo", port = serverPort)
                val text = response.receive<String>()
                assertEquals(body, text)
            }

        }

        assertTrue(observerExecuted)
    }
}
