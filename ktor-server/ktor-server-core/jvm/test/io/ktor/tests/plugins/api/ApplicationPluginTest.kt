package io.ktor.tests.plugins.api

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.application.plugins.api.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.ktor.util.*
import org.junit.Test
import kotlin.test.*

class ApplicationPluginTest {
    @Test
    fun `test empty plugin does not break pipeline`(): Unit = withTestApplication {
        val plugin = createApplicationPlugin("F", createConfiguration = {}) {
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

        val plugin = createApplicationPlugin("F", createConfiguration = { Config() }) {
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
        val plugin = createApplicationPlugin("F", createConfiguration = { }) {
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
        val pluginF = createApplicationPlugin("F", {}) {
            onCallRespond { call ->
                val data = call.attributes.getOrNull(FConfig.Key)
                if (data != null) {
                    transformResponseBody { data }
                }
            }
        }

        val pluginG = createApplicationPlugin("G", {}) {
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
        val pluginF = createApplicationPlugin("F", { ConfigWithData() }) {
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

        val plugin = createApplicationPlugin("F") {
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

        val plugin = createApplicationPlugin("F") {
            onCall { call ->
                eventsList.add("onCall")

                call.afterFinish {
                    eventsList.add("afterFinish")
                }
            }
            onCallReceive { call ->
                eventsList.add("onCallReceive")

                call.afterFinish {
                    eventsList.add("afterFinish")
                }
            }
            onCallRespond { call ->
                eventsList.add("onCallRespond")

                call.afterFinish {
                    eventsList.add("afterFinish")
                }
            }
            onCallRespond.afterTransform { call, _ ->
                eventsList.add("onCallRespond.afterTransform")

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

    @Test
    fun `test routing scoped install`() {
        class Config(var data: String)

        val plugin = createRouteScopedPlugin("F", { Config("default") }) {
            onCall {
                context.call.respond(pluginConfig.data)
            }
        }

        withTestApplication {
            application.routing {
                get { }
                route("/top") {
                    install(plugin) { data = "/top" }

                    get { }

                    route("/nested1") {
                        install(plugin) { data = "/nested1" }

                        get { }

                        route("/nested") {
                            install(plugin)

                            get { }
                        }
                    }
                    route("/nested2") {
                        install(plugin) { data = "/nested2" }

                        get { }
                    }
                }
            }

            handleRequest(HttpMethod.Get, "/top").let {
                assertEquals("/top", it.response.content)
            }

            handleRequest(HttpMethod.Get, "/top/nested1").let {
                assertEquals("/nested1", it.response.content)
            }

            handleRequest(HttpMethod.Get, "/top/nested2").let {
                assertEquals("/nested2", it.response.content)
            }

            handleRequest(HttpMethod.Get, "/top/nested1/nested").let {
                assertEquals("default", it.response.content)
            }
        }
    }

    @Test
    fun `test dependent routing scoped plugins`() {
        val pluginF = createRouteScopedPlugin("F", {}) {
            onCallRespond { call ->
                val data = call.attributes.getOrNull(FConfig.Key)
                if (data != null) {
                    transformResponseBody { data }
                }
            }
        }

        val pluginG = createRouteScopedPlugin("G", {}) {
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
            application.routing {
                route("a") {
                    install(pluginF)
                    route("b") {
                        install(pluginG)

                        get("/request") {
                            call.respondText("response")
                        }
                    }
                }
            }

            handleRequest(HttpMethod.Get, "/a/b/request") {
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

    @Test
    fun `test dependent routing scoped and application plugins`() {
        val pluginF = createApplicationPlugin("F", {}) {
            onCallRespond { call ->
                val data = call.attributes.getOrNull(FConfig.Key)
                if (data != null) {
                    transformResponseBody { data }
                }
            }
        }

        val pluginG = createRouteScopedPlugin("G", {}) {
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
            application.routing {
                route("a") {
                    route("b") {
                        install(pluginG)

                        get("/request") {
                            call.respondText("response")
                        }
                    }
                }
            }

            handleRequest(HttpMethod.Get, "/a/b/request") {
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
}
