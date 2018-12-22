package io.ktor.client.tests

import io.ktor.application.*
import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.client.features.*
import io.ktor.client.request.*
import io.ktor.client.tests.utils.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import kotlinx.coroutines.*
import kotlin.test.*

abstract class BuildersTest(val factory: HttpClientEngineFactory<*>) : TestWithKtor() {
    override val server: ApplicationEngine = embeddedServer(Netty, serverPort) {
        routing {
            get("/empty") {
                call.respondText("")
            }
            get("/hello") {
                call.respondText("hello")
            }
        }
    }

    @Test
    fun getEmptyResponseTest() = clientTest(factory) {
        test { client ->
            val response = client.get<String>(path = "/empty", port = serverPort)
            assertEquals("", response)
        }
    }

    @Test
    fun testNotFound() = clientTest(factory) {
        test { client ->
            assertFailsWith<BadResponseStatusException> {
                runBlocking {
                    client.get<String>(path = "/notFound", port = serverPort)
                }
            }
        }
    }

    @Test
    fun testDefaultRequest() = clientTest(factory) {
        test { rawClient ->

            val client = rawClient.config {
                defaultRequest {
                    port = serverPort
                }
            }

            assertEquals("hello", client.get<String>(path = "hello"))
        }
    }
}


