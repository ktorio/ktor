/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.application

import io.ktor.client.request.*
import io.ktor.server.response.*
import io.ktor.server.testing.*
import io.ktor.utils.io.concurrent.*
import kotlin.test.*

class HooksTest {

    @Test
    fun testCustomHookExecuted() {
        class HookHandler {
            var called: Boolean by shared(false)
            var executed: Boolean by shared(false)
        }

        val handler = HookHandler()

        val myHook = object : Hook<HookHandler.() -> Unit> {
            override fun install(application: Application, config: HookHandler.() -> Unit) {
                handler.apply(config)

                application.intercept(ApplicationCallPipeline.Call) {
                    handler.executed = true
                }
            }
        }

        val testPlugin = createApplicationPlugin("TestPlugin") {
            on(myHook) {
                called = true
            }
        }

        assertFalse(handler.called)

        testApplication {
            install(testPlugin)

            routing {
                handle {
                    call.respondText("OK")
                }
            }

            assertFalse(handler.executed)
            client.get("/")

            assertTrue(handler.executed)
        }

        assertTrue(handler.called)
    }

    @Test
    fun testShutdownHook() {
        class State {
            var shutdownCalled by shared(false)
        }

        val state = State()
        val shutdownHandler = createApplicationPlugin("ShutdownHandler") {
            on(Shutdown) {
                state.shutdownCalled = true
            }
        }

        testApplication {
            install(shutdownHandler)

            routing {
                handle {
                    call.respondText("OK")
                }
            }

            client.get("/")

            assertFalse(state.shutdownCalled)
        }

        assertTrue(state.shutdownCalled)
    }

    @Test
    fun testOnCallFailedHook() {
        class State {
            var fail: Throwable? by shared(null)
        }

        val state = State()

        val failedHandler = createApplicationPlugin("FailedHandler") {
            on(CallFailed) { _, cause ->
                state.fail = cause
            }
        }

        testApplication {
            install(failedHandler)

            routing {
                handle {
                    error("Failure")
                }
            }

            assertNull(state.fail)

            runCatching {
                client.get("/")
            }

            assertNotNull(state.fail)
            assertEquals("Failure", state.fail?.message)
        }
    }
}
