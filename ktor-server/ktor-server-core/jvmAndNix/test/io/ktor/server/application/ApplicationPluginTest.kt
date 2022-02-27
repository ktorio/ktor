/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.application

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.ktor.util.*
import io.ktor.util.collections.*
import io.ktor.utils.io.*
import io.ktor.utils.io.concurrent.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.*
import kotlin.test.*

@Suppress("DEPRECATION")
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

        assertEquals("response", handleRequest(HttpMethod.Get, "/request").response.content)
    }

    @Test
    fun `test plugin with single interception`() {
        data class Config(var enabled: Boolean = true)

        val plugin = createApplicationPlugin("F", createConfiguration = { Config() }) {
            onCall { call ->
                if (this@createApplicationPlugin.pluginConfig.enabled) {
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
            onCallRespond { call, _ ->
                val data = call.attributes.getOrNull(key)
                if (data != null) {
                    transformBody {
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

    var onCallProcessedTimes = 0

    @Test
    fun `test same phase defined twice`() {
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

            handleRequest(HttpMethod.Get, "/request")
            assertEquals(2, onCallProcessedTimes)
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
            onCallRespond { call, _ ->
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
            onCall { call ->
                call.respond(pluginConfig.data)
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

    var globalSideEffect = ""

    @Test
    fun `test side effect of install called on every installation`() {
        val TestPlugin = createRouteScopedPlugin("P") {
            globalSideEffect += "Called!"
        }

        withTestApplication {
            application.routing {
                route("/1") {
                    install(TestPlugin)
                }
                route("/2") {
                    install(TestPlugin)
                }
            }
        }
    }

    @Test
    fun testTransformBody() = withTestApplication {
        data class MyInt(val x: Int)

        val plugin = createApplicationPlugin("F") {
            onCallReceive { _ ->
                transformBody { data ->
                    val type = requestedType?.type!!
                    if (type != MyInt::class) return@transformBody data

                    MyInt(data.readInt())
                }
            }
            onCallRespond { _, _ ->
                transformBody { data ->
                    if (data !is MyInt) return@transformBody data

                    return@transformBody ByteChannel(false).apply {
                        writeInt(data.x)
                        close()
                    }
                }
            }
        }

        application.install(plugin)

        application.routing {
            post("/receive") {
                val data = call.receive<MyInt>()
                val newData = MyInt(data.x + 1)
                call.respond(newData)
            }
        }

        val call = handleRequest(HttpMethod.Post, "/receive") {
            setBody(
                buildPacket {
                    writeInt(100501)
                }
            )
        }

        runBlocking {
            val content = call.response.contentChannel()!!.readInt()
            assertEquals(100502, content)
        }
    }
}
