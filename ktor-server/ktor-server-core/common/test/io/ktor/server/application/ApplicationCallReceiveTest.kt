/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.application

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.content.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.ktor.util.pipeline.*
import kotlinx.coroutines.*
import kotlin.test.*

class ApplicationCallReceiveTest {

    @Test
    fun testReceiveFromReceivePipelineFailsWithoutRecursion() = testApplication {
        var interceptions = 0
        application {
            receivePipeline.intercept(ApplicationReceivePipeline.Before) {
                interceptions++
                val cause = assertFailsWith<RequestAlreadyConsumedException> {
                    call.receiveText()
                }
                assertEquals(
                    "Request body has already been consumed or is currently being received.",
                    cause.message
                )
            }

            routing {
                post("/") {
                    call.respondText(call.receiveText())
                }
            }
        }

        val response = client.post("/") {
            setBody("bodyContent")
        }
        assertEquals("bodyContent", response.bodyAsText())
        assertEquals(1, interceptions)
    }

    @Test
    fun testConcurrentReceiveFailsWithoutEnteringPipeline() = testApplication {
        val firstReceiveEntered = CompletableDeferred<Unit>()
        val releaseFirstReceive = CompletableDeferred<Unit>()
        var interceptions = 0

        application {
            receivePipeline.intercept(ApplicationReceivePipeline.Before) {
                interceptions++
                firstReceiveEntered.complete(Unit)
                releaseFirstReceive.await()
            }

            routing {
                post("/") {
                    coroutineScope {
                        val firstReceive = async { call.receiveText() }
                        firstReceiveEntered.await()
                        try {
                            assertFailsWith<RequestAlreadyConsumedException> {
                                call.receiveText()
                            }
                        } finally {
                            releaseFirstReceive.complete(Unit)
                        }
                        call.respondText(firstReceive.await())
                    }
                }
            }
        }

        val response = client.post("/") {
            setBody("bodyContent")
        }
        assertEquals("bodyContent", response.bodyAsText())
        assertEquals(1, interceptions)
    }

    @Test
    fun testReceiveNonNullable() = testApplication {
        val nullPlugin = createApplicationPlugin("NullPlugin") {
            onCallReceive { _ ->
                transformBody {
                    NullBody
                }
            }
        }

        install(nullPlugin)

        routing {
            post("/") {
                val result: String = try {
                    call.receive()
                } catch (cause: Throwable) {
                    cause.message ?: cause.toString()
                }

                call.respond(result)
            }
        }

        val response = client.post("/").bodyAsText()
        try {
            assertEquals("Cannot transform this request's content to kotlin.String", response)
        } catch (cause: Throwable) {
            // on JS/Wasm there is no package name
            assertEquals("Cannot transform this request's content to String", response)
        }
    }
}
