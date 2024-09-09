/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.http

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlinx.coroutines.*
import java.util.concurrent.*
import kotlin.test.*

class RespondWriteTest {
    @Test
    fun smoke() = testApplication {
        routing {
            get("/") {
                call.respondTextWriter { write("OK") }
            }
        }

        client.get("/").let { response ->
            assertEquals("OK", response.bodyAsText())
        }
    }

    @Test
    fun testFailureInside() = testApplication {
        routing {
            get("/") {
                call.respondTextWriter {
                    throw IllegalStateException("expected")
                }
            }
        }

        assertFailsWith<IllegalStateException> {
            client.get("/")
        }
    }

    @Test
    fun testSuspendInside() = testApplication {
        val executor = Executors.newSingleThreadExecutor()
        application {
            monitor.subscribe(ApplicationStopped) { executor.shutdown() }
        }
        routing {
            get("/") {
                call.respondTextWriter {
                    withContext(executor.asCoroutineDispatcher()) {
                        write("OK")
                    }
                }
            }
        }

        client.get("/").let { response ->
            assertEquals("OK", response.bodyAsText())
        }
    }

    @Test
    fun testFailureInsideUnresolvedCase() = testApplication {
        routing {
            get("/") {
                call.respondTextWriter {
                    close() // after that point the main pipeline is going to continue since the channel is closed
                    // so we can't simply bypass an exception
                    // the workaround is to hold pipeline on channel pipe until commit rather than just close

                    Thread.sleep(1000)
                    throw IllegalStateException("expected")
                }
            }
        }

        assertFailsWith<IllegalStateException> {
            client.get("/")
        }
    }
}
