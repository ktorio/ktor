package io.ktor.client.tests

import io.ktor.application.*
import io.ktor.client.engine.*
import io.ktor.client.request.*
import io.ktor.client.response.*
import io.ktor.client.tests.utils.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.util.*
import kotlinx.coroutines.*
import kotlin.test.*

abstract class AttributesTest(val factory: HttpClientEngineFactory<*>) : TestWithKtor() {
    override val server: ApplicationEngine = embeddedServer(Netty, serverPort) {
        routing {
            get("/hello") {
                call.respondText("hello")
            }
        }
    }

    @Test
    fun passAttributesTest() = clientTest(factory) {
        val attrKey = AttributeKey<String>("my-key")

        config {
            install("attr-test") {
                receivePipeline.intercept(HttpReceivePipeline.After) {
                    val attr = it.call.request.attributes[attrKey]

                    assertEquals("test-data", attr)
                }
            }
        }

        test { client ->
            val response = client.get<String>(path = "/hello", port = serverPort) {
                setAttributes {
                    put(attrKey, "test-data")
                }
            }

            assertEquals("hello", response)
        }
    }
}
