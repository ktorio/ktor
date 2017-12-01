package io.ktor.client.tests

import io.ktor.application.*
import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.response.*
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


open class FullFormTest(private val factory: HttpClientEngineFactory<*>) : TestWithKtor() {
    override val server = embeddedServer(Jetty, serverPort) {
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
    fun testGet() = runBlocking {
        val client = HttpClient(factory)
        val text = client.call {
            url {
                scheme = "http"
                host = "127.0.0.1"
                port = serverPort
                path = "/hello"
                method = HttpMethod.Get
                body = "Hello, server"
            }
        }.use { it.response.readText() }

        assertEquals("Hello, client", text)

        client.close()
    }

    @Test
    fun testPost() = runBlocking {
        val client = HttpClient(factory)
        val text = client.call {
            url {
                scheme = "http"
                host = "127.0.0.1"
                port = serverPort
                path = "/hello"
                method = HttpMethod.Post
                body = "Hello, server"
            }
        }.use { it.response.readText() }

        assertEquals("Hello, client", text)
        client.close()
    }

    @Test
    fun testRequest() {
        val client = HttpClient(factory)

        val requestBuilder = request {
            url {
                host = "localhost"
                scheme = "http"
                port = serverPort
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