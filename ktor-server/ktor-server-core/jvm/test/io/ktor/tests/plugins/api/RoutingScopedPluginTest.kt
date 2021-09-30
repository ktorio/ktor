/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.plugins.api

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.ktor.util.*
import kotlin.test.*

class RoutingScopedPluginTest {

    @Test
    fun testPluginInstalledTopLevel() = withTestApplication {
        val callbackResults = mutableListOf<String>()
        val receiveCallbackResults = mutableListOf<String>()
        val sendCallbackResults = mutableListOf<String>()
        val allCallbacks = listOf(callbackResults, receiveCallbackResults, sendCallbackResults)

        application.install(TestPlugin) {
            name = "foo"
            desc = "test plugin"
            addCallbacks(callbackResults, receiveCallbackResults, sendCallbackResults)
        }

        application.routing {
            route("root") {
                handle {
                    call.respond(call.receive<String>())
                }

                route("plugin1") {
                    install(TestPlugin) {
                        name = "bar"
                        addCallbacks(callbackResults, receiveCallbackResults, sendCallbackResults)
                    }

                    handle {
                        call.respond(call.receive<String>())
                    }
                }

                route("plugin2") {
                    install(TestPlugin) {
                        name = "baz"
                        addCallbacks(callbackResults, receiveCallbackResults, sendCallbackResults)
                    }

                    handle {
                        call.respond(call.receive<String>())
                    }
                }
            }

            handle {
                call.respond(call.receive<String>())
            }
        }

        on("making get request to /root") {
            val result = handleRequest {
                uri = "/root"
                method = HttpMethod.Post
                setBody("test")
            }
            it("should be handled") {
                assertEquals(HttpStatusCode.OK, result.response.status())
            }
            it("callback should be invoked") {
                allCallbacks.forEach {
                    assertEquals(1, it.size)
                    assertEquals("foo test plugin", it[0])
                    it.clear()
                }
            }
        }

        on("making get request to /root/plugin1") {
            val result = handleRequest {
                uri = "/root/plugin1"
                method = HttpMethod.Post
                setBody("test")
            }
            it("should be handled") {
                assertEquals(HttpStatusCode.OK, result.response.status())
            }
            it("callback should be invoked") {
                allCallbacks.forEach {
                    assertEquals(1, it.size)
                    assertEquals("bar defaultDesc", it[0])
                    it.clear()
                }
            }
        }

        on("making get request to /root/plugin2") {
            val result = handleRequest {
                uri = "/root/plugin2"
                method = HttpMethod.Post
                setBody("test")
            }
            it("should be handled") {
                assertEquals(HttpStatusCode.OK, result.response.status())
            }
            it("callback should be invoked") {
                allCallbacks.forEach {
                    assertEquals(1, it.size)
                    assertEquals("baz defaultDesc", it[0])
                    it.clear()
                }
            }
        }
    }

    @Test
    fun testPluginInstalledInRoutingScope() = withTestApplication {
        val callbackResults = mutableListOf<String>()
        val receiveCallbackResults = mutableListOf<String>()
        val sendCallbackResults = mutableListOf<String>()
        val allCallbacks = listOf(callbackResults, receiveCallbackResults, sendCallbackResults)

        application.routing {
            route("root-no-plugin") {
                route("first-plugin") {
                    install(TestPlugin) {
                        name = "foo"
                        desc = "test plugin"
                        addCallbacks(callbackResults, receiveCallbackResults, sendCallbackResults)
                    }

                    handle {
                        call.respond(call.receive<String>())
                    }

                    route("inner") {
                        route("new-plugin") {
                            install(TestPlugin) {
                                name = "bar"
                                addCallbacks(callbackResults, receiveCallbackResults, sendCallbackResults)
                            }

                            route("inner") {
                                handle {
                                    call.respond(call.receive<String>())
                                }
                            }

                            handle {
                                call.respond(call.receive<String>())
                            }
                        }

                        handle {
                            call.respond(call.receive<String>())
                        }
                    }
                }

                handle {
                    call.respond(call.receive<String>())
                }
            }
        }

        on("making get request to /root-no-plugin") {
            val result = handleRequest {
                uri = "/root-no-plugin"
                method = HttpMethod.Post
                setBody("test")
            }
            it("should be handled") {
                assertEquals(HttpStatusCode.OK, result.response.status())
            }
            it("callback should not be invoked") {
                allCallbacks.forEach {
                    assertEquals(0, it.size)
                }
            }
        }

        on("making get request to /root-no-plugin/first-plugin") {
            val result = handleRequest {
                uri = "/root-no-plugin/first-plugin"
                method = HttpMethod.Post
                setBody("test")
            }
            it("should be handled") {
                assertEquals(HttpStatusCode.OK, result.response.status())
            }
            it("callback should be invoked") {
                allCallbacks.forEach {
                    assertEquals(1, it.size)
                    assertEquals("foo test plugin", it[0])
                    it.clear()
                }
            }
        }

        on("making get request to /root-no-plugin/first-plugin/inner") {
            val result = handleRequest {
                uri = "/root-no-plugin/first-plugin/inner"
                method = HttpMethod.Post
                setBody("test")
            }
            it("should be handled") {
                assertEquals(HttpStatusCode.OK, result.response.status())
            }
            it("callback should be invoked") {
                allCallbacks.forEach {
                    assertEquals(1, it.size)
                    assertEquals("foo test plugin", it[0])
                    it.clear()
                }
            }
        }

        on("making get request to /root-no-plugin/first-plugin/inner/new-plugin") {
            val result = handleRequest {
                uri = "/root-no-plugin/first-plugin/inner/new-plugin"
                method = HttpMethod.Post
                setBody("test")
            }
            it("should be handled") {
                assertEquals(HttpStatusCode.OK, result.response.status())
            }
            it("callback should be invoked") {
                allCallbacks.forEach {
                    assertEquals(1, it.size)
                    assertEquals("bar defaultDesc", it[0])
                    it.clear()
                }
            }
        }

        on("making get request to /root-no-plugin/first-plugin/inner/new-plugin/inner") {
            val result = handleRequest {
                uri = "/root-no-plugin/first-plugin/inner/new-plugin/inner"
                method = HttpMethod.Post
                setBody("test")
            }
            it("should be handled") {
                assertEquals(HttpStatusCode.OK, result.response.status())
            }
            it("callback should be invoked") {
                allCallbacks.forEach {
                    assertEquals(1, it.size)
                    assertEquals("bar defaultDesc", it[0])
                    it.clear()
                }
            }
        }
    }

    @Test
    fun testPluginDoNotReuseConfig() = withTestApplication {
        val callbackResults = mutableListOf<String>()
        val receiveCallbackResults = mutableListOf<String>()
        val sendCallbackResults = mutableListOf<String>()
        val allCallbacks = listOf(callbackResults, receiveCallbackResults, sendCallbackResults)

        application.routing {
            route("root") {
                install(TestPlugin) {
                    name = "foo"
                    desc = "test plugin"
                    addCallbacks(callbackResults, receiveCallbackResults, sendCallbackResults)
                }
                route("plugin1") {
                    install(TestPlugin) {
                        desc = "new desc"
                        addCallbacks(callbackResults, receiveCallbackResults, sendCallbackResults)
                    }

                    handle {
                        call.respond(call.receive<String>())
                    }

                    route("plugin2") {
                        install(TestPlugin) {
                            name = "new name"
                            addCallbacks(callbackResults, receiveCallbackResults, sendCallbackResults)
                        }

                        handle {
                            call.respond(call.receive<String>())
                        }
                    }
                }

                handle {
                    call.respond(call.receive<String>())
                }
            }
        }

        on("making get request to /root") {
            val result = handleRequest {
                uri = "/root"
                method = HttpMethod.Post
                setBody("test")
            }
            it("should be handled") {
                assertEquals(HttpStatusCode.OK, result.response.status())
            }
            it("callback should be invoked") {
                allCallbacks.forEach {
                    assertEquals(1, it.size)
                    assertEquals("foo test plugin", it[0])
                    it.clear()
                }
            }
        }

        on("making get request to /root/plugin1") {
            val result = handleRequest {
                uri = "/root/plugin1"
                method = HttpMethod.Post
                setBody("test")
            }
            it("should be handled") {
                assertEquals(HttpStatusCode.OK, result.response.status())
            }
            it("callback should be invoked") {
                allCallbacks.forEach {
                    assertEquals(1, it.size)
                    assertEquals("defaultName new desc", it[0])
                    it.clear()
                }
            }
        }

        on("making get request to /root/plugin1/plugin2") {
            val result = handleRequest {
                uri = "/root/plugin1/plugin2"
                method = HttpMethod.Post
                setBody("test")
            }
            it("should be handled") {
                assertEquals(HttpStatusCode.OK, result.response.status())
            }
            it("callback should be invoked") {
                allCallbacks.forEach {
                    assertEquals(1, it.size)
                    assertEquals("new name defaultDesc", it[0])
                    it.clear()
                }
            }
        }
    }

    @Test
    fun testPluginMergedInstallationsAndLastWins() = withTestApplication {
        val callbackResults = mutableListOf<String>()
        val receiveCallbackResults = mutableListOf<String>()
        val sendCallbackResults = mutableListOf<String>()
        val allCallbacks = listOf(callbackResults, receiveCallbackResults, sendCallbackResults)

        application.routing {
            route("root") {
                install(TestPlugin) {
                    name = "foo"
                    desc = "first plugin"
                    addCallbacks(callbackResults, receiveCallbackResults, sendCallbackResults)
                }
                get("a") {
                    call.respond(call.receive<String>())
                }
            }
            route("root") {
                install(TestPlugin) {
                    name = "bar"
                    desc = "second plugin"
                    addCallbacks(callbackResults, receiveCallbackResults, sendCallbackResults)
                }
                get("b") {
                    call.respond(call.receive<String>())
                }
            }
        }

        on("making get request to /root/a") {
            val result = handleRequest {
                uri = "/root/a"
                method = HttpMethod.Get
                setBody("test")
            }
            it("should be handled") {
                assertEquals(HttpStatusCode.OK, result.response.status())
            }
            it("second callback should be invoked") {
                allCallbacks.forEach {
                    assertEquals(1, it.size)
                    assertEquals("bar second plugin", it[0])
                    it.clear()
                }
            }
        }

        on("making get request to /root/b") {
            val result = handleRequest {
                uri = "/root/b"
                method = HttpMethod.Get
                setBody("test")
            }
            it("should be handled") {
                assertEquals(HttpStatusCode.OK, result.response.status())
            }
            it("second callback should be invoked") {
                allCallbacks.forEach {
                    assertEquals(1, it.size)
                    assertEquals("bar second plugin", it[0])
                    it.clear()
                }
            }
        }
    }

    private fun TestPlugin.Config.addCallbacks(
        callbackResults: MutableList<String>,
        receiveCallbackResults: MutableList<String>,
        sendCallbackResults: MutableList<String>
    ) {
        pipelineCallback = { callbackResults.add(it) }
        receivePipelineCallback = { receiveCallbackResults.add(it) }
        sendPipelineCallback = { sendCallbackResults.add(it) }
    }
}

class TestPlugin {

    fun install(pipeline: Route) {
        pipeline.intercept(ApplicationCallPipeline.Plugins) {
            config.pipelineCallback("${config.name} ${config.desc}")
        }
        pipeline.receivePipeline.intercept(ApplicationReceivePipeline.Before) {
            config.receivePipelineCallback("${config.name} ${config.desc}")
        }
        pipeline.sendPipeline.intercept(ApplicationSendPipeline.Before) {
            config.sendPipelineCallback("${config.name} ${config.desc}")
        }
    }

    data class Config(
        var name: String = "defaultName",
        var desc: String = "defaultDesc",
        var pipelineCallback: (String) -> Unit = {},
        var receivePipelineCallback: (String) -> Unit = {},
        var sendPipelineCallback: (String) -> Unit = {},
    )

    companion object Plugin : SubroutePlugin<Config, TestPlugin> {

        override val key: AttributeKey<TestPlugin> = AttributeKey("TestPlugin")

        override fun install(pipeline: Route): TestPlugin {
            val plugin = TestPlugin()
            return plugin.apply { install(pipeline) }
        }

        override fun createConfiguration(): Config = Config()
    }
}
