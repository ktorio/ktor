package io.ktor.client.tests

import io.ktor.application.*
import io.ktor.client.*
import io.ktor.client.backend.*
import io.ktor.client.call.*
import io.ktor.client.tests.utils.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.jetty.*
import kotlinx.coroutines.experimental.*
import org.junit.Test
import org.junit.Assert.*


open class FullFormTests(factory: HttpClientBackendFactory) : TestWithKtor(factory) {
    override val server = embeddedServer(Jetty, port) {
        routing {
            get("/hello") {
                assertEquals("Hello, server", call.receive<String>())
                call.respondText("Hello, client")
            }
            post("/hello") {
                assertEquals("Hello, server", call.receive<String>())
                call.respondText("Hello, client")
            }
        }
    }

    @Test
    fun testGet() {
        val client = createClient()
        runBlocking {
            val text = client.call {
                url {
                    scheme = "http"
                    host = "127.0.0.1"
                    port = super.port
                    path = "/hello"
                    method = HttpMethod.Get
                    body = "Hello, server"
                }
            }.readText()

            assertEquals("Hello, client", text)
        }

        client.close()
    }

    @Test
    fun testPost() {
        val client = createClient()
        runBlocking {
            val text = client.call {
                url {
                    scheme = "http"
                    host = "127.0.0.1"
                    port = super.port
                    path = "/hello"
                    method = HttpMethod.Post
                    body = "Hello, server"
                }
            }.readText()

            assertEquals("Hello, client", text)
        }

        client.close()
    }

    @Test
    fun testRequest() {
        val client = createClient()

        val requestBuilder = request {
            url {
                host = "localhost"
                scheme = "http"
                port = super.port
                path = "/hello"
                method = HttpMethod.Get
                body = "Hello, server"
            }
        }

        val body = runBlocking { client.request<String>(requestBuilder) }
        assert(body == "Hello, client")

        client.close()
    }
}