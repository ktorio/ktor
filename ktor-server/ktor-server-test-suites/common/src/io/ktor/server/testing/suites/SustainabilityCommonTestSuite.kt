/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.testing.suites

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.engine.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.test.base.*
import io.ktor.util.PlatformUtils
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlin.test.*

abstract class SustainabilityCommonTestSuite<
    TEngine : ApplicationEngine,
    TConfiguration : ApplicationEngine.Configuration
    >(
    hostFactory: ApplicationEngineFactory<TEngine, TConfiguration>
) : EngineTestBase<TEngine, TConfiguration>(hostFactory) {

    @Test
    fun testRepeatRequest() = runTest {
        createAndStartServer {
            get("/") {
                call.respond("OK ${call.request.queryParameters["i"]}")
            }
        }

        for (i in 1..100) {
            withUrl("/?i=$i") {
                assertEquals(200, status.value)
                assertEquals("OK $i", bodyAsText())
            }
        }
    }

    @Test
    open fun testChunkedWithVSpace() = runTest {
        createAndStartServer {
            post("/") {
                call.receiveParameters()
                call.respond(HttpStatusCode.BadRequest, "")
            }
        }

        val request = listOf(
            "POST / HTTP/1.1\r\n",
            "Host:localhost\r\n",
            "Connection: close\r\n",
            "Content-Type: application/x-www-form-urlencoded\r\n",
            "Content-Length: 1\r\n",
            "Transfer-Encoding:\u000bchunked\r\n",
            "\r\n",
            "3\r\n",
            "a=1\r\n",
            "0\r\n",
            "\r\n"
        ).joinToString("")

        withRawSocket(port) {
            output.writeStringUtf8(request)
            output.flush()
            val status = input.readHttpLine()
            assertTrue(
                status.startsWith("HTTP/1.1 400") || status.startsWith("HTTP/1.0 400"),
                "Invalid response: $status"
            )
        }
    }

    @Test
    fun testChunkedIsNotFinal() = runTest {
        createAndStartServer {
            get("/") {
                call.respondText("Hello, world!", ContentType.Text.Html)
            }
            post("/") {
                call.receiveParameters()
                fail("We should NOT receive any content")
            }
        }

        val request = listOf(
            "POST / HTTP/1.1\r\n",
            "Host:localhost\r\n",
            "Connection: close\r\n",
            "Content-Length: 1\r\n",
            "Content-Type: application/x-www-form-urlencoded\r\n",
            "Transfer-Encoding: chunked, smuggle\r\n",
            "\r\n",
            "3\r\n",
            "a=1\r\n",
            "0\r\n",
            "\r\n"
        ).joinToString("")

        withRawSocket(port) {
            output.writeStringUtf8(request)
            output.flush()
            val status = input.readHttpLine()
            assertTrue(
                status.startsWith("HTTP/1.1 501") ||
                    status.startsWith("HTTP/1.1 400") ||
                    status.startsWith("HTTP/1.0 400"),
                "Invalid response: $status"
            )
        }
    }

    @Test
    fun testDoubleHost() = runTest {
        createAndStartServer {
            get("/") {
                call.respond("OK")
            }
        }

        withRawSocket(port) {
            output.writeStringUtf8(
                "GET / HTTP/1.1\r\n" +
                    "Host: www.example.com\r\n" +
                    "Host: www.example2.com\r\n" +
                    "Connection: close\r\n\r\n"
            )
            output.flush()

            assertContains(input.readHttpLine(), "400")
        }
    }

    @Test
    open fun testJobsAreCancelledOnShutdown() = runTest {
        var applicationJob: Job? = null
        var routingJob: Job? = null
        val applicationStarted = CompletableDeferred<Unit>()
        val routingStarted = CompletableDeferred<Unit>()

        suspend fun waitForever(started: CompletableDeferred<Unit>) {
            started.complete(Unit)
            delay(Long.MAX_VALUE)
        }

        val server = createAndStartServer {
            applicationJob = application.launch { waitForever(applicationStarted) }

            get("/launch-job") {
                routingJob = call.launch { waitForever(routingStarted) }
                call.respondText("Job launched")
            }
        }

        withUrl("/launch-job") {
            assertEquals("Job launched", call.body<String>())
        }

        withTimeout(5000) {
            applicationStarted.await()
            routingStarted.await()
        }

        assertNotNull(applicationJob, "Application job should not be null")
        assertNotNull(routingJob, "Routing job should not be null")
        assertTrue(applicationJob.isActive, "Application job should be active")
        assertTrue(routingJob.isActive, "Routing job should be active")

        if (PlatformUtils.IS_NATIVE) {
            server.stop(gracePeriodMillis = 1000, timeoutMillis = 5000)
        } else {
            server.stopSuspend(gracePeriodMillis = 1000, timeoutMillis = 5000)
        }

        assertTrue(applicationJob.isCancelled, "Application job should be canceled")
        assertTrue(routingJob.isCancelled, "Routing job should be canceled")
    }
}
