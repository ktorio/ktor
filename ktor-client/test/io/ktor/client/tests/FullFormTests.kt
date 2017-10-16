package io.ktor.client.tests

import io.ktor.client.HttpClient
import io.ktor.client.backend.jvm.ApacheBackend
import io.ktor.client.call.call
import io.ktor.client.receiveText
import io.ktor.client.request
import io.ktor.client.tests.utils.TestWithKtor
import io.ktor.host.embeddedServer
import io.ktor.http.HttpMethod
import io.ktor.jetty.Jetty
import io.ktor.pipeline.call
import io.ktor.request.receive
import io.ktor.response.respondText
import io.ktor.routing.get
import io.ktor.routing.post
import io.ktor.routing.routing
import kotlinx.coroutines.experimental.runBlocking
import org.junit.Test


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