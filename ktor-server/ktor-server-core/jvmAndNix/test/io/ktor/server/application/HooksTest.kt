/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.application

import io.ktor.client.request.*
import io.ktor.server.response.*
import io.ktor.server.testing.*
import kotlin.test.*

class HooksTest {

    @Test
    fun testCustomHookExecuted() {
        class HookHandler {
            var called: Boolean = false
            var executed: Boolean = false
        }

        val currentHandler = HookHandler()

        val myHook = object : Hook<HookHandler.() -> Unit> {
            override fun install(application: ApplicationCallPipeline, handler: HookHandler.() -> Unit) {
                currentHandler.apply(handler)

                application.intercept(ApplicationCallPipeline.Call) {
                    currentHandler.executed = true
                }
            }
        }

        val testPlugin = createApplicationPlugin("TestPlugin") {
            on(myHook) {
                called = true
            }
        }

        assertFalse(currentHandler.called)

        testApplication {
            install(testPlugin)

            routing {
                handle {
                    call.respondText("OK")
                }
            }

            assertFalse(currentHandler.executed)
            client.get("/")

            assertTrue(currentHandler.executed)
        }

        assertTrue(currentHandler.called)
    }

    @Test
    fun testShutdownHook() {
        class State {
            var shutdownCalled = false
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
            var fail: Throwable? = null
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
