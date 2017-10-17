package io.ktor.client.tests

import io.ktor.client.*
import io.ktor.client.backend.jvm.*
import io.ktor.client.call.*
import io.ktor.client.tests.utils.*
import io.ktor.host.*
import io.ktor.http.*
import io.ktor.jetty.*
import io.ktor.pipeline.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import kotlinx.coroutines.experimental.*
import org.junit.*


class FullFormTests : TestWithKtor() {
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
        val client = HttpClient(ApacheBackend)
        runBlocking {
            val text = client.call {
                url {
                    scheme = "http"
                    host = "localhost"
                    port = 8080
                    path = "hello"
                    method = HttpMethod.Get
                }
            }.receiveText()

            assert(text == "Hello, world")
        }

        client.close()
    }

    @Test
    fun testRequest() {
        val client = HttpClient(ApacheBackend)

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