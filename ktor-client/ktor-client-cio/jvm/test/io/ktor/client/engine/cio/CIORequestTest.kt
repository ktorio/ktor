/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.cio

import io.ktor.client.call.*
import io.ktor.client.network.sockets.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.client.test.base.*
import io.ktor.client.tests.utils.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.*
import io.mockk.mockkStatic
import io.mockk.verify
import kotlinx.coroutines.*
import kotlinx.coroutines.debug.junit5.CoroutinesTimeout
import java.net.InetAddress
import java.nio.channels.UnresolvedAddressException
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds

@CoroutinesTimeout(60_000)
class CIORequestTest : TestWithKtor() {
    private val testSize = 2 * 1024

    private val firstPipelineRequestReceived = CompletableDeferred<Unit>()
    private val secondPipelineRequestReceived = CompletableDeferred<Unit>()

    override val server: EmbeddedServer<*, *> = embeddedServer(Netty, serverPort) {
        routing {
            param("param") {
                get {
                    call.respond(call.parameters["param"]!!)
                }
            }
            get("/") {
                val longHeader = call.request.headers["LongHeader"]!!
                call.respond(
                    object : OutgoingContent.NoContent() {
                        override val headers: Headers = headersOf("LongHeader", longHeader)
                    }
                )
            }
            get("/echo") {
                call.respond("OK")
            }
            get("/echo-body") {
                call.respondText(call.receiveText())
            }
            get("/delay") {
                delay(1.seconds)
                call.respond("OK")
            }
            get("/pipeline") {
                when (call.parameters["request"]) {
                    "first" -> {
                        firstPipelineRequestReceived.complete(Unit)
                        secondPipelineRequestReceived.await()
                    }

                    "second" -> secondPipelineRequestReceived.complete(Unit)
                }
                call.respond("OK")
            }
        }
    }

    @Test
    fun testTwoPipelinedRequests() = testWithEngine(CIO) {
        config {
            defaultRequest { port = serverPort }
            engine {
                pipelining = true
                endpoint {
                    maxConnectionsPerRoute = 1
                }
            }
        }

        test { client ->
            val responses = coroutineScope {
                val firstRequest = async { client.get("/pipeline?request=first").bodyAsText() }
                firstPipelineRequestReceived.await()
                val secondRequest = async { client.get("/pipeline?request=second").bodyAsText() }
                awaitAll(firstRequest, secondRequest)
            }
            assertEquals(listOf("OK", "OK"), responses)
        }
    }

    @Test
    fun testPipelinedRequestBodiesAreWrittenSequentially() = testWithEngine(CIO) {
        config {
            defaultRequest { port = serverPort }
            engine {
                pipelining = true
                endpoint {
                    maxConnectionsPerRoute = 1
                }
            }
        }

        test { client ->
            fun HttpRequestBuilder.setBody(value: String, beforeWrite: suspend () -> Unit) {
                val body = object : OutgoingContent.WriteChannelContent() {
                    override val contentLength: Long = value.length.toLong()

                    override suspend fun writeTo(channel: ByteWriteChannel) {
                        beforeWrite()
                        channel.writeStringUtf8(value)
                    }
                }
                setBody(body)
            }

            val firstBodyStarted = CompletableDeferred<Unit>()
            val secondBodyStarted = CompletableDeferred<Unit>()

            val responses = coroutineScope {
                val firstRequest = async {
                    client.get("/echo-body") {
                        setBody("first") {
                            firstBodyStarted.complete(Unit)
                            assertNull(
                                withTimeoutOrNull(1.seconds) { secondBodyStarted.await() },
                                "Second body write shouldn't be started",
                            )
                        }
                    }.bodyAsText()
                }
                firstBodyStarted.await()
                val secondRequest = async {
                    client.get("/echo-body") {
                        setBody("second") { secondBodyStarted.complete(Unit) }
                    }.bodyAsText()
                }
                awaitAll(firstRequest, secondRequest)
            }

            assertEquals(listOf("first", "second"), responses)
        }
    }

    @Test
    fun engineUsesRequestTimeoutFromItsConfiguration() {
        testWithEngine(CIO) {
            config {
                engine {
                    requestTimeout = 10
                }
            }

            test { client ->
                assertFailsWith<HttpRequestTimeoutException> {
                    client.prepareGet { url(path = "/delay", port = serverPort) }.execute()
                }
            }
        }
    }

    @Test
    @Ignore
    fun testTimeoutPriority() {
        testWithEngine(CIO) {
            config {
                engine {
                    requestTimeout = 2000
                }

                install(HttpTimeout) {
                    requestTimeoutMillis = 1
                }
            }

            test { client ->
                assertFailsWith<HttpRequestTimeoutException> {
                    client.prepareGet { url(path = "/delay", port = serverPort) }.execute()
                }
            }
        }

        testWithEngine(CIO) {
            config {
                engine {
                    requestTimeout = 1
                }

                install(HttpTimeout) {
                    requestTimeoutMillis = 2000
                }
            }

            test { client ->
                client.prepareGet { url(path = "/delay", port = serverPort) }.execute()
            }
        }
    }

    @Test
    fun longHeadersTest() = testWithEngine(CIO) {
        test { client ->
            val headerValue = "x".repeat(testSize)

            client.prepareGet {
                url(port = serverPort)
                header("LongHeader", headerValue)
            }.execute { response ->
                assertEquals(headerValue, response.headers["LongHeader"])
            }
        }
    }

    @Test
    fun testParameterWithoutPath() = testWithEngine(CIO) {
        test { client ->
            client.prepareGet {
                url(port = serverPort)
                parameter("param", "value")
            }.execute { response ->
                assertEquals("value", response.bodyAsText())
            }
        }
    }

    @Test
    fun testHangingTimeoutWithWrongUrl() = testWithEngine(CIO) {
        config {
            engine {
                endpoint {
                    connectTimeout = 1
                }
            }
        }

        test { client ->
            var fail: Throwable? = null
            for (i in 0..1000) {
                try {
                    client.get("http://something.wrong").body<String>()
                } catch (cause: Throwable) {
                    fail = cause
                }
            }

            assertNotNull(fail)
            if (fail !is ConnectTimeoutException && fail !is UnresolvedAddressException) {
                fail("Expected ConnectTimeoutException or UnresolvedAddressException, got $fail", fail)
            }
        }
    }

    @Test
    fun testInetAddressRetrievedOnce() = testWithEngine(CIO) {
        test { client ->
            mockkStatic(InetAddress::getByName) {
                client.prepareGet { url(path = "/echo", port = serverPort) }.execute()
                verify(exactly = 1) { InetAddress.getByName(any()) }
            }
        }
    }
}
