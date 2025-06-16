/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.testing.suites

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.application.hooks.ResponseSent
import io.ktor.server.application.install
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.ApplicationEngineFactory
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingRoot
import io.ktor.server.routing.get
import io.ktor.server.test.base.EngineTestBase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

abstract class HooksTestSuite<TEngine : ApplicationEngine, TConfiguration : ApplicationEngine.Configuration>(
    hostFactory: ApplicationEngineFactory<TEngine, TConfiguration>
) : EngineTestBase<TEngine, TConfiguration>(hostFactory) {

    override fun plugins(application: Application, routingConfig: Route.() -> Unit) {
        application.install(RoutingRoot, routingConfig)
    }

    @Test
    fun responseSentBlockCalledOnException() = runTest {
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
}
