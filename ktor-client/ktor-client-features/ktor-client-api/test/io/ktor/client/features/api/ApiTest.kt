package io.ktor.client.features.api

import io.ktor.application.call
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.tests.utils.TestWithKtor
import io.ktor.client.tests.utils.clientTest
import io.ktor.client.tests.utils.config
import io.ktor.client.tests.utils.test
import io.ktor.response.respondText
import io.ktor.routing.get
import io.ktor.routing.routing
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.jetty.Jetty
import kotlin.test.Test
import kotlin.test.assertEquals

class ApiTest : TestWithKtor() {
    override val server: ApplicationEngine = embeddedServer(Jetty, serverPort) {
        routing {
            get("/some/base/path") {
                call.respondText("Some Text")
            }
            get("/some/base/path/test") {
                call.respondText("Some second Text")
            }
        }
    }

    @Test
    fun testApi() = clientTest(CIO) {
        config {
            api {
                port = serverPort
                basePath = "/some/base/path"
            }
        }

        test { client ->

            val someText = client.get<String>()
            assertEquals("Some Text", someText)

            val someSecondText = client.get<String>(path = "/test")
            assertEquals("Some second Text", someSecondText)
        }
    }
}