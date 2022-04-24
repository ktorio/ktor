/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.server.testing

import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.server.application.*
import io.ktor.server.config.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlin.test.*
import io.ktor.client.plugins.websocket.WebSockets as ClientWebSockets

class TestApplicationTestJvm {

    @Test
    fun testDefaultConfig() = testApplication {
        application {
            val config = environment.config
            routing {
                get("a") {
                    call.respond(config.property("ktor.test").getString())
                }
            }
        }

        val response = client.get("a")
        assertEquals("test_value", response.bodyAsText())
    }

    @Test
    fun testWebSockets() = testApplication {
        install(WebSockets)
        routing {
            webSocket("/echo") {
                for (message in incoming) {
                    outgoing.send(message)
                }
            }
        }

        val client = createClient { install(ClientWebSockets) }
        client.ws("/echo") {
            outgoing.send(Frame.Text("Hello"))
            repeat(100) {
                val frame = incoming.receive() as Frame.Text
                assertEquals("Hello" + ".".repeat(it), frame.readText())
                outgoing.send(Frame.Text(frame.readText() + "."))
            }
        }
    }

    @Test
    fun testCustomEnvironmentKeepsDefaultProperties() = testApplication {
        environment {
            rootPath = "root/path"
        }
        routing {
            val config = application.environment.config
            get("a") {
                call.respond(config.property("ktor.test").getString())
            }
        }

        val response = client.get("root/path/a")
        assertEquals("test_value", response.bodyAsText())
    }

    @Test
    fun testCustomConfig() = testApplication {
        environment {
            config = ApplicationConfig("application-custom.conf")
        }
        routing {
            val config = application.environment.config
            get {
                call.respond(config.property("ktor.test").getString())
            }
        }

        val response = client.get("/")
        assertEquals("another_test_value", response.bodyAsText())
    }

    @Test
    fun testCustomYamlConfig() = testApplication {
        environment {
            config = ApplicationConfig("application-custom.yaml")
        }
        routing {
            val config = application.environment.config
            get {
                call.respond(config.property("ktor.test").getString())
            }
        }

        val response = client.get("/")
        assertEquals("another_test_value", response.bodyAsText())
    }

    @Test
    fun testConfigLoadsModules() = testApplication {
        environment {
            config = ApplicationConfig("application-with-modules.conf")
        }

        val response = client.get("/")
        assertEquals("OK FROM MODULE", response.bodyAsText())
    }

    public fun Application.module() {
        routing {
            get { call.respond("OK FROM MODULE") }
        }
    }
}
