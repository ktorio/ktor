/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.testing.suites

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.application.hooks.ResponseSent
import io.ktor.server.application.install
import io.ktor.server.application.setCoroutineFailureHandler
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.ApplicationEngineFactory
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingRoot
import io.ktor.server.routing.application
import io.ktor.server.routing.get
import io.ktor.server.test.base.EngineTestBase
import io.ktor.utils.io.InternalAPI
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withTimeout
import kotlinx.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

abstract class HooksTestSuite<TEngine : ApplicationEngine, TConfiguration : ApplicationEngine.Configuration>(
    hostFactory: ApplicationEngineFactory<TEngine, TConfiguration>
) : EngineTestBase<TEngine, TConfiguration>(hostFactory) {

    override fun plugins(application: Application, routingConfig: Route.() -> Unit) {
        application.install(RoutingRoot, routingConfig)
    }

    @Test
    fun responseSentBlockCalledOnException() = runTest(retries = 3) {
        var responseSentCalled = false
        createAndStartServer {
            install(
                createRouteScopedPlugin("Plugin") {
                    on(ResponseSent) {
                        responseSentCalled = true
                    }
                }
            )

            get("/") {
                throw BadRequestException("Bad Request")
            }
        }

        withUrl("/") {
            assertEquals(HttpStatusCode.BadRequest, status)
            assertTrue(responseSentCalled, message = "ResponseSent was not called")
        }
    }

    @OptIn(InternalAPI::class)
    @Test
    fun coroutineFailuresReachApplicationHandler() = runTest {
        val failures = Channel<String>(Channel.UNLIMITED)
        createAndStartServer {
            application.setCoroutineFailureHandler { _, exception ->
                failures.trySend(exception.message.orEmpty())
                true
            }

            get("/supervised") {
                // The engine drops an IOException unless the application's handler takes it
                supervisorScope { launch { throw IOException("supervised") } }
                call.respondText("ok")
            }
            get("/application") {
                call.application.launch { throw IllegalStateException("application") }
                call.respondText("ok")
            }
        }

        withUrl("/supervised") {
            assertEquals(HttpStatusCode.OK, status)
        }
        withUrl("/application") {
            assertEquals(HttpStatusCode.OK, status)
        }

        // withUrl may send a request more than once, over different protocols
        val seen = mutableSetOf<String>()
        withTimeout(5.seconds) {
            while (seen.size < 2) seen += failures.receive()
        }
        assertEquals(setOf("supervised", "application"), seen)
    }
}
