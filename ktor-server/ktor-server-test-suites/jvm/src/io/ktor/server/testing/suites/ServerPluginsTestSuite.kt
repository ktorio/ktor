/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.server.testing.suites

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.ktor.util.collections.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.*
import org.junit.*
import org.junit.Assert.*

@Suppress("DEPRECATION")
abstract class ServerPluginsTestSuite<TEngine : ApplicationEngine, TConfiguration : ApplicationEngine.Configuration>(
    hostFactory: ApplicationEngineFactory<TEngine, TConfiguration>
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

    val plugin = createApplicationPlugin("F") {
        onCall { call ->
            sendEvent("onCall")

            call.afterFinish {
                sendEvent("afterFinish")
            }
        }
        onCallReceive { call ->
            sendEvent("onCallReceive")

            call.afterFinish {
                sendEvent("afterFinish")
            }
        }
        onCallRespond { call, _ ->
            sendEvent("onCallRespond")

            call.afterFinish {
                sendEvent("afterFinish")
            }
        }
        onCallRespond.afterTransform { call, _ ->
            sendEvent("onCallRespond.afterTransform")

            call.afterFinish {
                sendEvent("afterFinish")
            }
        }
    }

    val expectedEventsForCall = listOf(
        "onCall",
        "onCallReceive",
        "onCallRespond",
        "onCallRespond.afterTransform",
        "afterFinish",
        "afterFinish",
        "afterFinish",
        "afterFinish"
    )

    override fun plugins(application: Application, routingConfigurer: Routing.() -> Unit) {
        super.plugins(application, routingConfigurer)

        application.install(plugin)
    }

    @Test
    fun testAfterFinishOrder() {
        createAndStartServer {
            get("/request") {
                val data = call.receive<String>()
                call.respondText("response: $data")
            }
        }

        setNumberOfEvents(expectedEventsForCall.size)

        assertEvents(expectedEventsForCall, 20000) { checker ->
            withUrl("/request") { checker() }
        }
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
