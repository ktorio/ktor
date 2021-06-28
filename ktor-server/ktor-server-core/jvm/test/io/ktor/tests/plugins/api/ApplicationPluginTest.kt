/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.plugins.api

import io.ktor.application.*
import io.ktor.application.plugins.api.ApplicationPlugin.Companion.createPlugin
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.testing.*
import io.ktor.util.*
import kotlin.test.*

class ApplicationPluginTest {
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
            onRequest { call ->
                if (this@createPlugin.pluginConfig.enabled) {
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
//        assertWithPlugin(pluginEnabled = true, expectedResponse = "Plugin enabled!")
    }

    @Test
    fun `test plugin with multiple phases`() {
        val plugin = createPlugin("F", createConfiguration = { }) {
            val key = AttributeKey<String>("FKey")

            onRequest { call ->
                val data = call.request.headers["F"]
                if (data != null) {
                    call.attributes.put(key, data)
                }
            }
            onCallRespond.beforeTransform { call ->
                val data = call.attributes.getOrNull(key)
                if (data != null) {
                    transformReceiveBody { data }
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
            onCallRespond.beforeTransform { call ->
                val data = call.attributes.getOrNull(FConfig.Key)
                if (data != null) {
                    transformReceiveBody { data }
                }
            }
        }

        val pluginG = createPlugin("G", {}) {
            beforePlugin(pluginF) {
                onCallRespond.beforeTransform { call ->
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
            onRequest { call ->
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
}
