package io.ktor.tests.plugins.api

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.application.plugins.api.ServerPlugin.Companion.createPlugin
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.ktor.util.*
import org.junit.Test
import kotlin.test.assertEquals

class ServerPluginTest {
    @Test
    fun `test empty plugin does not break pipeline`(): Unit = withTestApplication {
        val plugin = createPlugin("F", createConfiguration = {}) {
        }

        application.install(plugin)

        application.routing {
            get("/request") {
                call.respondText("response")
            }
        }

        handleRequest(HttpMethod.Get, "/request").let { call ->
            assertEquals("response", call.response.content)
        }
    }

    @Test
    fun `test plugin with single interception`() {
        data class Config(var enabled: Boolean = true)

        val plugin = createPlugin("F", createConfiguration = { Config() }) {
            onCall { call ->
                if (pluginConfig.enabled) {
                    call.respondText("Plugin enabled!")
                    finish()
                }
            }
        }

        fun assertWithPlugin(pluginEnabled: Boolean, expectedResponse: String) = withTestApplication {
            application.install(plugin) {
                enabled = pluginEnabled
            }

            application.routing {
                get("/request") {
                    call.respondText("response")
                }
            }

            handleRequest(HttpMethod.Get, "/request").let { call ->
                assertEquals(expectedResponse, call.response.content)
            }
        }

        assertWithPlugin(pluginEnabled = false, expectedResponse = "response")
    }

    @Test
    fun `test plugin with multiple phases`() {
        val plugin = createPlugin("F", createConfiguration = { }) {
            val key = AttributeKey<String>("FKey")

            onCall { call ->
                val data = call.request.headers["F"]
                if (data != null) {
                    call.attributes.put(key, data)
                }
            }
            onCallRespond { call ->
                val data = call.attributes.getOrNull(key)
                if (data != null) {
                    transformResponseBody {
                        data
                    }
                }
            }
        }

        fun assertWithPlugin(expectedResponse: String, data: String? = null) = withTestApplication {
            application.install(plugin)

            application.routing {
                get("/request") {
                    call.respondText("response")
                }
            }

            handleRequest(HttpMethod.Get, "/request") {
                if (data != null) {
                    addHeader("F", data)
                }
            }.let { call ->
                val content = call.response.content
                assertEquals(expectedResponse, content)
            }
        }

        assertWithPlugin(expectedResponse = "response", data = null)
        assertWithPlugin(expectedResponse = "custom data", data = "custom data")
    }

    class FConfig {
        companion object {
            val Key = AttributeKey<String>("FKey")
        }
    }

    @Test
    fun `test dependent plugins`() {
        val pluginF = createPlugin("F", {}) {
            onCallRespond { call ->
                val data = call.attributes.getOrNull(FConfig.Key)
                if (data != null) {
                    transformResponseBody { data }
                }
            }
        }

        val pluginG = createPlugin("G", {}) {
            beforePlugins(pluginF) {
                onCallRespond { call ->
                    val data = call.request.headers["F"]
                    if (data != null) {
                        call.attributes.put(FConfig.Key, data)
                    }
                }
            }
        }

        fun assertWithPlugin(expectedResponse: String, data: String? = null) = withTestApplication {
            application.install(pluginF)
            application.install(pluginG)

            application.routing {
                get("/request") {
                    call.respondText("response")
                }
            }

            handleRequest(HttpMethod.Get, "/request") {
                if (data != null) {
                    addHeader("F", data)
                }
            }.let { call ->
                val content = call.response.content
                assertEquals(expectedResponse, content)
            }
        }

        assertWithPlugin(expectedResponse = "response", data = null)
        assertWithPlugin(expectedResponse = "custom data", data = "custom data")
    }

    class ConfigWithData {
        var data = ""
    }

    @Test
    fun `test multiple installs changing config`() {
        val pluginF = createPlugin("F", { ConfigWithData() }) {
            onCall { call ->
                val oldValue = pluginConfig.data
                pluginConfig.data = "newValue"
                val newValue = pluginConfig.data

                call.respondText("$oldValue:$newValue")
                finish()
            }
        }

        fun assertWithPlugin(expectedResponse: String) = withTestApplication {
            application.install(pluginF) {
                data = "oldValue"
            }

            application.routing {
                get("/request") {
                    call.respondText("response")
                }
            }

            handleRequest(HttpMethod.Get, "/request").let { call ->
                val content = call.response.content
                assertEquals(expectedResponse, content)
            }
        }

        assertWithPlugin(expectedResponse = "oldValue:newValue")
        assertWithPlugin(expectedResponse = "oldValue:newValue")
        assertWithPlugin(expectedResponse = "oldValue:newValue")
    }

    @Test
    fun `test same phase defined twice`() {
        var onCallProcessedTimes = 0

        val plugin = createPlugin("F") {
            onCall {
                onCallProcessedTimes += 1
            }

            onCall {
                onCallProcessedTimes += 1
            }
        }

        withTestApplication {
            application.install(plugin)

            application.routing {
                get("/request") {
                    call.respondText("response")
                }
            }

            handleRequest(HttpMethod.Get, "/request").let {
                assertEquals(2, onCallProcessedTimes)
            }
        }
    }

    @Test
    fun `test afterFinish is executed after plugins code`() {
        val eventsList = mutableListOf<String>()

        val plugin = createPlugin("F") {
            onCall { call ->
                eventsList.add("onCall")
                println(call)

                call.afterFinish {
                    eventsList.add("afterFinish")
                }
            }
            onCallReceive { call ->
                eventsList.add("onCallReceive")
                println(call)

                call.afterFinish {
                    eventsList.add("afterFinish")
                }
            }
            onCallRespond { call ->
                eventsList.add("onCallRespond")
                println(call)

                call.afterFinish {
                    eventsList.add("afterFinish")
                }
            }
            onCallRespond.afterTransform { call, _ ->
                eventsList.add("onCallRespond.afterTransform")
                println(call)

                call.afterFinish {
                    eventsList.add("afterFinish")
                }
            }
        }

        withTestApplication {
            application.install(plugin)

            application.routing {
                get("/request") {
                    val data = call.receive<String>()
                    call.respondText("response : $data")
                }
            }

            handleRequest(HttpMethod.Get, "/request") {
                this.setBody("data")
            }

            assertEquals(
                listOf(
                    "onCall",
                    "onCallReceive",
                    "onCallRespond",
                    "onCallRespond.afterTransform",
                    "afterFinish",
                    "afterFinish",
                    "afterFinish",
                    "afterFinish"
                ),
                eventsList
            )
        }
    }
}
