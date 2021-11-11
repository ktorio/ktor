/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.server.testing

import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.cio.websocket.*
import io.ktor.server.application.*
import io.ktor.server.application.plugins.api.*
import io.ktor.server.config.*
import io.ktor.server.plugins.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.ktor.server.testing.client.*
import io.ktor.server.websocket.*
import io.ktor.server.websocket.WebSockets
import io.ktor.util.*
import kotlinx.coroutines.*
import kotlin.test.*
import io.ktor.client.plugins.websocket.WebSockets.Plugin as ClientWebSockets

class TestApplicationTest {

    private val TestPlugin = createApplicationPlugin("test_plugin") {
        onCall { call ->
            if (!call.request.headers.contains("request_header")) {
                throw BadRequestException("Error")
            }
            call.response.header("response_header", call.request.headers["request_header"]!!)
        }
    }
    private val testApplication = TestApplication {
        install(TestPlugin)
        routing {
            get("a") {
                call.respond("OK")
            }
        }
    }

    private object TestClientPlugin : HttpClientPlugin<Unit, TestClientPlugin> {
        override val key: AttributeKey<TestClientPlugin> = AttributeKey("testClientPlugin")

        override fun prepare(block: Unit.() -> Unit) = TestClientPlugin

        override fun install(plugin: TestClientPlugin, scope: HttpClient) {
            scope.sendPipeline.intercept(HttpSendPipeline.State) {
                context.headers.append("request_header", "value_1")
            }
        }
    }

    private val testClient = testApplication.createClient {
        install(TestClientPlugin)
    }

    @Test
    fun testTopLevel() = runBlocking {
        val response = testClient.get("a")
        assertEquals("OK", response.bodyAsText())
        assertEquals("value_1", response.headers["response_header"])
    }

    @Test
    fun testDefaultConfig() = withTestApplication1 {
        application {
            val config = environment.config
            install(TestPlugin)
            routing {
                get("a") {
                    call.respond(config.property("ktor.test").getString())
                }
            }
        }

        val client = createClient { install(TestClientPlugin) }
        val response = client.get("a")
        assertEquals("test_value", response.bodyAsText())
        assertEquals("value_1", response.headers["response_header"])
    }

    @Test
    fun testApplicationBlock() = withTestApplication1 {
        application {
            install(TestPlugin)
            routing {
                get("a") {
                    call.respond("OK")
                }
            }
        }

        val client = createClient { install(TestClientPlugin) }
        val response = client.get("a")
        assertEquals("OK", response.bodyAsText())
        assertEquals("value_1", response.headers["response_header"])
    }

    @Test
    fun testBridge() = withTestApplication1 {
        install(TestPlugin)
        routing {
            get("a") {
                call.respond("OK")
            }
        }

        val client = createClient { install(TestClientPlugin) }
        val response = client.get("a")
        assertEquals("OK", response.bodyAsText())
        assertEquals("value_1", response.headers["response_header"])
    }

    @Test
    fun testWebSockets() = withTestApplication1 {
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
    fun testExternalServices() = withTestApplication1 {
        externalServices {
            hosts("http://www.google.com:123", "https://google.com") {
                routing {
                    get { call.respond("EXTERNAL OK") }
                }
            }
        }

        routing {
            get { call.respond("OK") }
        }

        val internal = client.get("/")
        assertEquals("OK", internal.bodyAsText())

        val external1 = client.get("//www.google.com:123")
        assertEquals("EXTERNAL OK", external1.bodyAsText())
        val external2 = client.get("https://google.com")
        assertEquals("EXTERNAL OK", external2.bodyAsText())
        assertFailsWith<InvalidTestRequestException> { client.get("//google.com:123") }
        assertFailsWith<InvalidTestRequestException> { client.get("//google.com") }
        assertFailsWith<InvalidTestRequestException> { client.get("https://www.google.com") }
    }

    @Test
    fun testCustomEnvironmentKeepsDefaultProperties() = withTestApplication1 {
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
    fun testCustomConfig() = withTestApplication1 {
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
    fun testConfigLoadsModules() = withTestApplication1 {
        environment {
            config = ApplicationConfig("application-with-modules.conf")
        }

        val response = client.get("/")
        assertEquals("OK FROM MODULE", response.bodyAsText())
    }

    @Test
    fun testCanAccessClient() = withTestApplication1 {
        class TestPluginConfig {
            lateinit var pluginClient: HttpClient
        }

        val TestPlugin = createApplicationPlugin("test", ::TestPluginConfig) {
            onCall {
                val externalValue = client.get("https://test.com").bodyAsText()
                it.response.headers.append("test", externalValue)
            }
        }
        install(TestPlugin) {
            pluginClient = client
        }
        externalServices {
            hosts("https://test.com") {
                routing { get { call.respond("TEST_VALUE")} }
            }
        }
        routing {
            get {
                call.respond("OK")
            }
        }

        val response = client.get("/")
        assertEquals("OK", response.bodyAsText())
        assertEquals("TEST_VALUE", response.headers["test"])
    }

    public fun Application.module() {
        routing {
            get { call.respond("OK FROM MODULE") }
        }
    }
}
