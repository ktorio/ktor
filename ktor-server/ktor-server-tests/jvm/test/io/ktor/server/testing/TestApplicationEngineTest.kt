/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.testing

import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import io.ktor.utils.io.*
import io.ktor.utils.io.charsets.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.*
import kotlinx.io.*
import kotlinx.serialization.*
import java.util.concurrent.atomic.*
import kotlin.coroutines.*
import kotlin.system.*
import kotlin.test.*
import kotlin.text.Charsets

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

        val engine = startApplicationAndGetEngine()
        val elapsedTime = measureTimeMillis {
            engine.handleRequest(HttpMethod.Get, "/").let { call ->
                assertTrue(call.response.status()!!.isSuccess())
            }
        }
        assertEquals(listOf("Delay($delayTime)", "Delay($delayTime)"), delayLog)
        assertTrue { elapsedTime < (delayTime * 2) }
    }

    @Test
    fun testExceptionHandle() = testApplication {
        install(CallLogging)

        routing {
            get("/") {
                error("Handle me")
            }
        }

        val engine = startApplicationAndGetEngine()
        assertFails {
            engine.handleRequest(HttpMethod.Get, "/")
        }
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

        val engine = startApplicationAndGetEngine()
        with(engine.handleRequest(HttpMethod.Get, "/good")) {
            assertEquals(HttpStatusCode.OK, response.status())
            assertEquals("The Response", response.content)
        }

        with(engine.handleRequest(HttpMethod.Get, "/broken")) {
            assertEquals(HttpStatusCode.OK, response.status())
            assertEquals("The Response", response.content)
        }

        assertFailsWith<IllegalStateException> {
            engine.handleRequest(HttpMethod.Get, "/fail")
        }
    }

    @Test
    fun testResponseAwaitWithCustomPort() = testApplication {
        install(RoutingRoot) {
            port(7070) {
                get("/good") {
                    call.respond(HttpStatusCode.OK, "The Response")
                }
            }
        }

        val engine = startApplicationAndGetEngine()
        with(engine.handleRequest(HttpMethod.Get, "/good") { port = 7070 }) {
            assertEquals(HttpStatusCode.OK, response.status())
            assertEquals("The Response", response.content)
        }

        with(engine.handleRequest(HttpMethod.Get, "/good") { addHeader(HttpHeaders.Host, "localhost:7070") }) {
            assertEquals(HttpStatusCode.OK, response.status())
            assertEquals("The Response", response.content)
        }
    }

    @Test
    fun testHookRequests() = testApplication {
        val numberOfRequestsProcessed = AtomicInteger(0)
        val numberOfResponsesProcessed = AtomicInteger(0)

        val expectedNumberOfCalls = 1

        routing {
            get("/") {
                call.respond(HttpStatusCode.NoContent)
            }
        }

        val engine = startApplicationAndGetEngine()

        // Injecting the hooks and checking they are invoked only once
        engine.hookRequests(
            processRequest = { setup ->
                numberOfRequestsProcessed.incrementAndGet()
                setup()
            },
            processResponse = { numberOfResponsesProcessed.incrementAndGet() }
        ) {
            engine.handleRequest(HttpMethod.Get, "/").apply {
                assertEquals(expectedNumberOfCalls, numberOfRequestsProcessed.get())
                assertEquals(expectedNumberOfCalls, numberOfResponsesProcessed.get())
            }
        }

        // Outside hookRequests scope original processors are restored
        // so further requests should not increment the counters
        engine.handleRequest(HttpMethod.Get, "/").apply {
            assertEquals(expectedNumberOfCalls, numberOfRequestsProcessed.get())
            assertEquals(expectedNumberOfCalls, numberOfResponsesProcessed.get())
        }
    }

    @Test
    fun testCookiesSession() = testApplication {

        @Serializable
        data class CountSession(val count: Int)

        install(Sessions) {
            cookie<CountSession>("MY_SESSION")
        }

        routing {
            get("/") {
                val session = call.sessions.getOrSet { CountSession(0) }
                call.sessions.set(session.copy(count = session.count + 1))
                call.respond(HttpStatusCode.OK, "${session.count}")
            }
        }

        val engine = startApplicationAndGetEngine()

        fun doRequestAndCheckResponse(expected: String) {
            engine.handleRequest(HttpMethod.Get, "/").apply { assertEquals(expected, response.content) }
        }

        // By default it doesn't preserve cookies
        doRequestAndCheckResponse("0")
        doRequestAndCheckResponse("0")

        // Inside a cookiesSession block cookies are preserved.
        engine.cookiesSession {
            doRequestAndCheckResponse("0")
            doRequestAndCheckResponse("1")
        }

        // Starting another cookiesSession block doesn't preserve cookies from previous blocks.
        engine.cookiesSession {
            doRequestAndCheckResponse("0")
            doRequestAndCheckResponse("1")
            doRequestAndCheckResponse("2")
        }
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
        val multipart = listOf(
            PartData.FileItem(
                { ByteReadChannel("BODY".toByteArray()) },
                {},
                headersOf(
                    HttpHeaders.ContentDisposition,
                    ContentDisposition.File
                        .withParameter(ContentDisposition.Parameters.Name, "file")
                        .withParameter(ContentDisposition.Parameters.FileName, "test.jpg")
                        .toString()
                )
            )
        )

        val engine = startApplicationAndGetEngine()
        val response = engine.handleRequest(HttpMethod.Post, "/multipart") {
            addHeader(
                HttpHeaders.ContentType,
                ContentType.MultiPart.FormData.withParameter("boundary", boundary).toString()
            )
            bodyChannel = buildMultipart(boundary, multipart)
        }.response
        assertEquals(HttpStatusCode.OK, response.status())
    }

    private suspend fun ApplicationTestBuilder.startApplicationAndGetEngine(): TestApplicationEngine {
        var engine: TestApplicationEngine? = null
        application { engine = this.engine as TestApplicationEngine }
        startApplication()
        return checkNotNull(engine)
    }
}

@OptIn(DelicateCoroutinesApi::class)
internal fun buildMultipart(
    boundary: String,
    parts: List<PartData>
): ByteReadChannel = GlobalScope.writer {
    if (parts.isEmpty()) return@writer

    try {
        append("\r\n\r\n")
        parts.forEach {
            append("--$boundary\r\n")
            for ((key, values) in it.headers.entries()) {
                append("$key: ${values.joinToString(";")}\r\n")
            }
            append("\r\n")
            append(
                when (it) {
                    is PartData.FileItem -> {
                        channel.writeFully(it.provider().readRemaining().readByteArray())
                        ""
                    }

                    is PartData.BinaryItem -> {
                        channel.writeFully(it.provider().readByteArray())
                        ""
                    }

                    is PartData.FormItem -> it.value
                    is PartData.BinaryChannelItem -> {
                        it.provider().copyTo(channel)
                        ""
                    }
                }
            )
            append("\r\n")
        }

        append("--$boundary--\r\n")
    } finally {
        parts.forEach { it.dispose() }
    }
}.channel

private suspend fun WriterScope.append(str: String, charset: Charset = Charsets.UTF_8) {
    channel.writeFully(str.toByteArray(charset))
}
