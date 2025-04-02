/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.testing

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.*
import kotlin.coroutines.*
import kotlin.system.*
import kotlin.test.*

class TestApplicationEngineTest {

    @Test
    fun testCustomDispatcher() = testApplication {
        @OptIn(InternalCoroutinesApi::class)
        fun CoroutineDispatcher.withDelay(delay: Delay): CoroutineDispatcher =
            object : CoroutineDispatcher(), Delay by delay {
                override fun isDispatchNeeded(context: CoroutineContext): Boolean =
                    this@withDelay.isDispatchNeeded(context)

                override fun dispatch(context: CoroutineContext, block: Runnable) =
                    this@withDelay.dispatch(context, block)
            }

        val delayLog = arrayListOf<String>()
        val delayTime = 10_000L

        routing {
            get("/") {
                delay(delayTime)
                delay(delayTime)
                call.respondText("OK")
            }
        }

        engine {
            @OptIn(InternalCoroutinesApi::class)
            dispatcher = Dispatchers.Unconfined.withDelay(
                object : Delay {
                    override fun scheduleResumeAfterDelay(
                        timeMillis: Long,
                        continuation: CancellableContinuation<Unit>
                    ) {
                        // Run immediately and log it
                        delayLog += "Delay($timeMillis)"
                        continuation.resume(Unit)
                    }
                }
            )
        }

        val elapsedTime = measureTimeMillis {
            client.get("/").let { response ->
                assertTrue(response.status.isSuccess())
            }
        }
        assertEquals(listOf("Delay($delayTime)", "Delay($delayTime)"), delayLog)
        assertTrue { elapsedTime < (delayTime * 2) }
    }

    @Test
    fun testResponseAwait() = testApplication {
        install(RoutingRoot) {
            get("/good") {
                call.respond(HttpStatusCode.OK, "The Response")
            }
            get("/broken") {
                delay(100)
                call.respond(HttpStatusCode.OK, "The Response")
            }
            get("/fail") {
                error("Handle me")
            }
        }

        client.get("/good").let { response ->
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("The Response", response.body())
        }

        client.get("/broken").let { response ->
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("The Response", response.body())
        }

        assertEquals(HttpStatusCode.InternalServerError, client.get("/fail").status)
    }

    @Test
    fun accessNotExistingRouteTest() = testApplication {
        routing {
            get("/exist") {
                call.respondText("Routing exist")
            }
        }

        val client = client.config { expectSuccess = false }

        val notExistingResponse = client.get("/notExist")
        assertEquals(HttpStatusCode.NotFound, notExistingResponse.status)

        val existingResponse = client.get("/exist")
        assertEquals(HttpStatusCode.OK, existingResponse.status)
    }

    @Test
    fun testMultipart() = testApplication {
        routing {
            post("/multipart") {
                call.receiveMultipart().readPart()
                call.respond(HttpStatusCode.OK, "OK")
            }
        }

        val boundary = "***bbb***"
        val multipart = formData {
            append(
                key = "file",
                value = "BODY".toByteArray(),
                headers = Headers.build {
                    append(HttpHeaders.ContentType, "image/jpg")
                    append(HttpHeaders.ContentDisposition, "filename=\"test.jpg\"")
                },
            )
        }

        val response = client.post("/multipart") {
            setBody(
                MultiPartFormDataContent(
                    multipart,
                    boundary,
                    ContentType.MultiPart.FormData.withParameter("boundary", boundary),
                )
            )
        }
        assertEquals(HttpStatusCode.OK, response.status)
    }
}
