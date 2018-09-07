package io.ktor.client.features.auth.basic

import io.ktor.application.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.tests.utils.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.jetty.*
import kotlin.test.*

class BasicAuthTestWithServer : TestWithKtor() {
    override val server: ApplicationEngine = embeddedServer(Jetty, serverPort) {
        routing {
            get("/") {
                val auth = call.request.headers[HttpHeaders.Authorization] ?: error("Auth header not found")
                val expected = BasicAuth.constructBasicAuthValue("testUsername", "testPassword")
                assertEquals(expected, auth)
                call.respondText("OK")
            }
        }
    }

    @Test
    fun testHandle() = clientTest(CIO){
        config {
            install(BasicAuth) {
                username = "testUsername"
                password = "testPassword"
            }

            test { client ->
                val response = client.get<String>(port = serverPort)
                assertEquals("OK", response)
            }
        }
    }

}