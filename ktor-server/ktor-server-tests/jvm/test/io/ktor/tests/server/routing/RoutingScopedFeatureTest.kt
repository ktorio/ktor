/*
 * Copyright 2014-2020 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.server.routing

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.testing.*
import io.ktor.util.*
import kotlin.test.*

class RoutingScopedFeatureTest {

    @Test
    fun testFeatureInstalledTopLevel() = withTestApplication {
        val callbackResults = mutableListOf<String>()
        val receiveCallbackResults = mutableListOf<String>()
        val sendCallbackResults = mutableListOf<String>()
        val allCallbacks = listOf(callbackResults, receiveCallbackResults, sendCallbackResults)

        application.install(TestFeature) {
            name = "foo"
            desc = "test feature"
            addCallbacks(callbackResults, receiveCallbackResults, sendCallbackResults)
        }

        application.routing {
            route("root") {
                handle {
                    call.respond(call.receive<String>())
                }

                route("feature1") {
                    install(TestFeature) {
                        name = "bar"
                        addCallbacks(callbackResults, receiveCallbackResults, sendCallbackResults)
                    }

                    handle {
                        call.respond(call.receive<String>())
                    }
                }

                route("feature2") {
                    install(TestFeature) {
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
                assertTrue(result.requestHandled)
            }
            it("callback should be invoked") {
                allCallbacks.forEach {
                    assertEquals(1, it.size)
                    assertEquals("foo test feature", it[0])
                    it.clear()
                }
            }
        }

        on("making get request to /root/feature1") {
            val result = handleRequest {
                uri = "/root/feature1"
                method = HttpMethod.Post
                setBody("test")
            }
            it("should be handled") {
                assertTrue(result.requestHandled)
            }
            it("callback should be invoked") {
                allCallbacks.forEach {
                    assertEquals(1, it.size)
                    assertEquals("bar defaultDesc", it[0])
                    it.clear()
                }
            }
        }

        on("making get request to /root/feature2") {
            val result = handleRequest {
                uri = "/root/feature2"
                method = HttpMethod.Post
                setBody("test")
            }
            it("should be handled") {
                assertTrue(result.requestHandled)
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
    fun testFeatureInstalledInRoutingScope() = withTestApplication {
        val callbackResults = mutableListOf<String>()
        val receiveCallbackResults = mutableListOf<String>()
        val sendCallbackResults = mutableListOf<String>()
        val allCallbacks = listOf(callbackResults, receiveCallbackResults, sendCallbackResults)

        application.routing {
            route("root-no-feature") {
                route("first-feature") {
                    install(TestFeature) {
                        name = "foo"
                        desc = "test feature"
                        addCallbacks(callbackResults, receiveCallbackResults, sendCallbackResults)
                    }

                    handle {
                        call.respond(call.receive<String>())
                    }

                    route("inner") {
                        route("new-feature") {
                            install(TestFeature) {
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

        on("making get request to /root-no-feature") {
            val result = handleRequest {
                uri = "/root-no-feature"
                method = HttpMethod.Post
                setBody("test")
            }
            it("should be handled") {
                assertTrue(result.requestHandled)
            }
            it("callback should not be invoked") {
                allCallbacks.forEach {
                    assertEquals(0, it.size)
                }
            }
        }

        on("making get request to /root-no-feature/first-feature") {
            val result = handleRequest {
                uri = "/root-no-feature/first-feature"
                method = HttpMethod.Post
                setBody("test")
            }
            it("should be handled") {
                assertTrue(result.requestHandled)
            }
            it("callback should be invoked") {
                allCallbacks.forEach {
                    assertEquals(1, it.size)
                    assertEquals("foo test feature", it[0])
                    it.clear()
                }
            }
        }

        on("making get request to /root-no-feature/first-feature/inner") {
            val result = handleRequest {
                uri = "/root-no-feature/first-feature/inner"
                method = HttpMethod.Post
                setBody("test")
            }
            it("should be handled") {
                assertTrue(result.requestHandled)
            }
            it("callback should be invoked") {
                allCallbacks.forEach {
                    assertEquals(1, it.size)
                    assertEquals("foo test feature", it[0])
                    it.clear()
                }
            }
        }

        on("making get request to /root-no-feature/first-feature/inner/new-feature") {
            val result = handleRequest {
                uri = "/root-no-feature/first-feature/inner/new-feature"
                method = HttpMethod.Post
                setBody("test")
            }
            it("should be handled") {
                assertTrue(result.requestHandled)
            }
            it("callback should be invoked") {
                allCallbacks.forEach {
                    assertEquals(1, it.size)
                    assertEquals("bar defaultDesc", it[0])
                    it.clear()
                }
            }
        }

        on("making get request to /root-no-feature/first-feature/inner/new-feature/inner") {
            val result = handleRequest {
                uri = "/root-no-feature/first-feature/inner/new-feature/inner"
                method = HttpMethod.Post
                setBody("test")
            }
            it("should be handled") {
                assertTrue(result.requestHandled)
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
    fun testFeatureDoNotReuseConfig() = withTestApplication {
        val callbackResults = mutableListOf<String>()
        val receiveCallbackResults = mutableListOf<String>()
        val sendCallbackResults = mutableListOf<String>()
        val allCallbacks = listOf(callbackResults, receiveCallbackResults, sendCallbackResults)

        application.routing {
            route("root") {
                install(TestFeature) {
                    name = "foo"
                    desc = "test feature"
                    addCallbacks(callbackResults, receiveCallbackResults, sendCallbackResults)
                }
                route("feature1") {
                    install(TestFeature) {
                        desc = "new desc"
                        addCallbacks(callbackResults, receiveCallbackResults, sendCallbackResults)
                    }

                    handle {
                        call.respond(call.receive<String>())
                    }

                    route("feature2") {
                        install(TestFeature) {
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
                assertTrue(result.requestHandled)
            }
            it("callback should be invoked") {
                allCallbacks.forEach {
                    assertEquals(1, it.size)
                    assertEquals("foo test feature", it[0])
                    it.clear()
                }
            }
        }

        on("making get request to /root/feature1") {
            val result = handleRequest {
                uri = "/root/feature1"
                method = HttpMethod.Post
                setBody("test")
            }
            it("should be handled") {
                assertTrue(result.requestHandled)
            }
            it("callback should be invoked") {
                allCallbacks.forEach {
                    assertEquals(1, it.size)
                    assertEquals("defaultName new desc", it[0])
                    it.clear()
                }
            }
        }

        on("making get request to /root/feature1/feature2") {
            val result = handleRequest {
                uri = "/root/feature1/feature2"
                method = HttpMethod.Post
                setBody("test")
            }
            it("should be handled") {
                assertTrue(result.requestHandled)
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
    fun testFeatureMergedInstallationsAndLastWins() = withTestApplication {
        val callbackResults = mutableListOf<String>()
        val receiveCallbackResults = mutableListOf<String>()
        val sendCallbackResults = mutableListOf<String>()
        val allCallbacks = listOf(callbackResults, receiveCallbackResults, sendCallbackResults)

        application.routing {
            route("root") {
                install(TestFeature) {
                    name = "foo"
                    desc = "first feature"
                    addCallbacks(callbackResults, receiveCallbackResults, sendCallbackResults)
                }
                get("a") {
                    call.respond(call.receive<String>())
                }
            }
            route("root") {
                install(TestFeature) {
                    name = "bar"
                    desc = "second feature"
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
                assertTrue(result.requestHandled)
            }
            it("second callback should be invoked") {
                allCallbacks.forEach {
                    assertEquals(1, it.size)
                    assertEquals("bar second feature", it[0])
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
                assertTrue(result.requestHandled)
            }
            it("second callback should be invoked") {
                allCallbacks.forEach {
                    assertEquals(1, it.size)
                    assertEquals("bar second feature", it[0])
                    it.clear()
                }
            }
        }
    }

    private fun TestFeature.Config.addCallbacks(
        callbackResults: MutableList<String>,
        receiveCallbackResults: MutableList<String>,
        sendCallbackResults: MutableList<String>
    ) {
        pipelineCallback = { callbackResults.add(it) }
        receivePipelineCallback = { receiveCallbackResults.add(it) }
        sendPipelineCallback = { sendCallbackResults.add(it) }
    }
}

class TestFeature {

    fun install(pipeline: ApplicationCallPipeline) {
        pipeline.intercept(ApplicationCallPipeline.Features) {
            val config = Config().apply(configurationBlock)
            config.pipelineCallback("${config.name} ${config.desc}")
        }
        pipeline.receivePipeline.intercept(ApplicationReceivePipeline.Before) {
            val config = Config().apply(configurationBlock)
            config.receivePipelineCallback("${config.name} ${config.desc}")
        }
        pipeline.sendPipeline.intercept(ApplicationSendPipeline.Before) {
            val config = Config().apply(configurationBlock)
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

    companion object Feature : RoutingScopedFeature<ApplicationCallPipeline, Config, TestFeature> {

        override val key: AttributeKey<TestFeature> = AttributeKey("TestFeature")

        override fun install(pipeline: ApplicationCallPipeline): TestFeature {
            val feature = TestFeature()
            return feature.apply { install(pipeline) }
        }
    }
}
