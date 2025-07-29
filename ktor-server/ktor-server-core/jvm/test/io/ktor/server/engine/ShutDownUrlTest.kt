/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.engine

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlinx.coroutines.*
import kotlin.test.*

class ShutDownUrlTest {

    @Test
    fun testShutdownUrlRespondsWithGone() = testApplication {
        var exitCode = CompletableDeferred<Int>()

        install(ShutDownUrl.ApplicationCallPlugin) {
            shutDownUrl = "/shutdown"
            exitCodeSupplier = { 42 }
            exit = exitCode::complete
        }

        routing {
            get("/normal") {
                call.respondText("Normal response")
            }
        }

        client.get("/normal").let { response ->
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("Normal response", response.bodyAsText())
        }

        assertEquals(HttpStatusCode.Gone, client.get("/shutdown").status)
        assertEquals(42, exitCode.await())
    }

    @Test
    fun testShutdownUnderLoad() = testApplication {
        val completedRequests = mutableListOf<Boolean>()
        val deferred = CompletableDeferred<Unit>()

        install(ShutDownUrl.ApplicationCallPlugin) {
            shutDownUrl = "/shutdown"
            exitCodeSupplier = { 42 }
            exit = {
                deferred.complete(Unit)
            }
        }

        routing {
            // Simulate a slow endpoint
            get("/slow") {
                delay(1000)
                completedRequests.add(true)
                call.respondText("Slow response")
            }
        }

        // Start multiple slow requests in parallel
        val slowJobs = List(5) {
            application.launch {
                try {
                    client.get("/slow")
                } catch (e: Exception) {
                    // Request may be canceled during shutdown
                }
            }
        }

        assertEquals(HttpStatusCode.Gone, client.get("/shutdown").status)

        withTimeout(5000) {
            deferred.await()
        }

        assertFalse(slowJobs.any { it.isActive }, "Expected jobs to be canceled")
    }

    @Test
    fun testExceptionHandlingDuringShutdown() = testApplication {
        val exitCode = CompletableDeferred<Int>()

        install(ShutDownUrl.ApplicationCallPlugin) {
            shutDownUrl = "/shutdown"
            exitCodeSupplier = {
                throw IllegalStateException("Something went wrong")
            }
            exit = exitCode::complete
        }

        // Call the shutdown endpoint
        assertEquals(HttpStatusCode.Gone, client.get("/shutdown").status)

        // Verify exit was called with error code 1 due to exception
        assertEquals(1, exitCode.await())
    }
}
