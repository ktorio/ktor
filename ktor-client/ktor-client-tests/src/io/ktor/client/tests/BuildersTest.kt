package io.ktor.client.tests

import io.ktor.application.*
import io.ktor.client.engine.*
import io.ktor.client.request.*
import io.ktor.client.tests.utils.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import kotlin.test.*

open class BuildersTest(val factory: HttpClientEngineFactory<*>) : TestWithKtor() {
    override val server: ApplicationEngine = embeddedServer(Netty, serverPort) {
        routing {
            get("/empty") {
                call.respondText("")
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
            val response = client.get<String>(path = "/notFound", port = serverPort)
            assertEquals("", response)
        }
    }
}