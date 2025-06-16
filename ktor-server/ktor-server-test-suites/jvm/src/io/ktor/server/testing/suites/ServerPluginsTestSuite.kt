/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.server.testing.suites

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.test.base.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.*
import kotlin.test.*

abstract class ServerPluginsTestSuite<TEngine : ApplicationEngine, TConfiguration : ApplicationEngine.Configuration>(
    hostFactory: ApplicationEngineFactory<TEngine, TConfiguration>
) : EngineTestBase<TEngine, TConfiguration>(hostFactory) {

    private var semaphore = Semaphore(1)
    private suspend fun setNumberOfEvents(n: Int) {
        semaphore = Semaphore(n)
        repeat(n) {
            semaphore.acquire()
        }
    }

    private suspend fun expectNumberOfEvents(n: Int) {
        repeat(n) {
            semaphore.acquire()
        }
    }

    private val eventsList = mutableListOf<String>()
    private fun sendEvent(event: String) {
        eventsList.add(event)
        semaphore.release()
    }

    private suspend fun assertEvents(
        events: List<String>,
        timeoutMillis: Long,
        withUrlBlock: suspend (suspend () -> Unit) -> Unit
    ) {
        eventsList.clear()
        setNumberOfEvents(events.size)

        withTimeout(timeoutMillis) {
            withUrlBlock {
                expectNumberOfEvents(events.size)

                assertEquals(events, eventsList.toList())
            }
        }
    }

    val plugin = createApplicationPlugin("F") {
        onCall {
            sendEvent("onCall")
        }
        onCallReceive { _ ->
            sendEvent("onCallReceive")
        }
        onCallRespond { _ ->
            sendEvent("onCallRespond")
        }
    }

    val expectedEventsForCall = listOf("onCall", "onCallReceive", "onCallRespond")

    override fun plugins(application: Application, routingConfig: Route.() -> Unit) {
        super.plugins(application, routingConfig)

        application.install(plugin)
    }

    @Test
    fun testCoroutineContextIsCreatedForSingleCallOnly() = runTest {
        createAndStartServer {
            get("/request") {
                val data = call.receive<String>()
                call.respondText("response: $data")
            }
        }

        assertEvents(expectedEventsForCall + expectedEventsForCall, 40000) { checker ->
            withUrl("/request") {
                withUrl("/request") {
                    checker()
                }
            }
        }
    }
}
