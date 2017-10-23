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
import io.ktor.server.host.*
import io.ktor.server.jetty.*
import kotlinx.coroutines.experimental.*
import org.junit.*


open class FullFormTests(factory: HttpClientBackendFactory) : TestWithKtor(factory) {
    override val server = embeddedServer(Jetty, 8080) {
        routing {
            get("/hello") {
                call.respondText("Hello, world")
            }
            post("/hello") {
                assert(call.receive<String>() == "Hello, world")
                call.respondText("")
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
                    host = "localhost"
                    port = 8080
                    path = "hello"
                    method = HttpMethod.Get
                }
            }.readText()

            assert(text == "Hello, world")
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
                port = 8080
                path = "hello"
                method = HttpMethod.Get }
        }

        val body = runBlocking { client.request<String>(requestBuilder) }
        assert(body == "Hello, world")

        client.close()
    }
}