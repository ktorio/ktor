package io.ktor.client.tests

import io.ktor.application.*
import io.ktor.client.call.*
import io.ktor.client.engine.*
import io.ktor.client.request.*
import io.ktor.client.response.*
import io.ktor.client.tests.utils.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.jetty.*
import io.ktor.server.netty.*
import io.ktor.util.*
import kotlin.test.*


@Suppress("KDocMissingDocumentation")
abstract class FullFormTest(private val factory: HttpClientEngineFactory<*>) : TestWithKtor() {
    override val server = embeddedServer(Jetty, serverPort) {
        routing {
            get("/hello") {
                assertEquals("Hello, server", call.receive())
                call.respondText("Hello, client")
            }
            post("/hello") {
                assertEquals("Hello, server", call.receive())
                call.respondText("Hello, client")
            }
            get("/custom") {
                call.respond(HttpStatusCode(200, "Custom"), "OK")
            }
        }
    }

    @Test
    fun testGet() = clientTest(factory) {
        test { client ->
            val text = client.call {
                url {
                    protocol = URLProtocol.HTTP
                    host = "127.0.0.1"
                    port = serverPort
                    encodedPath = "/hello"
                    method = HttpMethod.Get
                    body = "Hello, server"
                }
            }.use { it.response.readText() }

            assertEquals("Hello, client", text)
        }
    }

    @Test
    fun testPost() = clientTest(factory) {
        test { client ->
            val text = client.call {
                url {
                    protocol = URLProtocol.HTTP
                    host = "127.0.0.1"
                    port = serverPort
                    encodedPath = "/hello"
                    method = HttpMethod.Post
                    body = "Hello, server"
                }
            }.use { it.response.readText() }

            assertEquals("Hello, client", text)
        }
    }

    @Test
    fun testRequest() = clientTest(factory) {
        test { client ->
            val requestBuilder = request {
                url {
                    host = "localhost"
                    protocol = URLProtocol.HTTP
                    port = serverPort
                    encodedPath = "/hello"
                    method = HttpMethod.Get
                    body = "Hello, server"
                }
            }

            val body = client.request<String>(requestBuilder)
            assertEquals("Hello, client", body)
        }
    }

    @Test
    fun testCustomUrls() = clientTest(factory) {
        val urls = listOf(
            "https://google.com",
            "http://kotlinlang.org/",
            "https://kotlinlang.org/"
        )

        test { client ->
            urls.forEach {
                client.get<String>(it)
            }
        }
    }
}
