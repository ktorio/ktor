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
import kotlin.test.Test

abstract class ServerPluginsTestSuite<TEngine : ServerEngine, TConfiguration : ServerEngine.Configuration>(
    hostFactory: ServerEngineFactory<TEngine, TConfiguration>
) : EngineTestBase<TEngine, TConfiguration>(hostFactory) {

    private var semaphore = Semaphore(1)
    private fun setNumberOfEvents(n: Int) {
        semaphore = Semaphore(n)
        runBlocking {
            repeat(n) {
                semaphore.acquire()
            }
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

    private fun assertEvents(events: List<String>, timeoutMillis: Long, withUrlBlock: (suspend () -> Unit) -> Unit) {
        eventsList.clear()
        setNumberOfEvents(events.size)

        runBlocking {
            withTimeout(timeoutMillis) {
                withUrlBlock {
                    expectNumberOfEvents(events.size)

                    assertEquals(events, eventsList.toList())
                }
            }
        }
    }

    val plugin = createServerPlugin("F") {
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

    override fun plugins(server: Server, routingConfig: Route.() -> Unit) {
        super.plugins(server, routingConfig)

        server.install(plugin)
    }

    @Test
    fun testCoroutineContextIsCreatedForSingleCallOnly() {
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
