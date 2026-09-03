/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.server.netty.http3

import io.ktor.http.content.OutgoingContent
import io.ktor.server.http.push
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import io.ktor.server.plugins.origin
import io.ktor.server.response.UseHttp2Push
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.*
import io.ktor.server.test.base.EngineTestBase
import io.ktor.server.test.base.Http3TestResponse
import io.ktor.server.test.base.withHttp3Client
import io.ktor.server.testing.ExpectedTestException
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.ExperimentalKtorApi
import io.netty.handler.codec.http3.DefaultHttp3Headers
import io.netty.handler.codec.http3.Http3Headers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import org.slf4j.LoggerFactory
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.coroutines.CoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalKtorApi::class, UseHttp2Push::class)
class NettyHttp3ErrorTest :
    EngineTestBase<NettyApplicationEngine, NettyApplicationEngine.Configuration>(Netty) {

    init {
        enableSsl = true
        enableHttp3 = true
    }

    override fun configure(configuration: NettyApplicationEngine.Configuration) {
        configuration.enableHttp3()
    }

    @Test
    fun `a throwing handler produces a 500 over HTTP3`() = runTest {
        createAndStartServer {
            get("/boom") { throw ExpectedTestException("deliberate failure") }
        }

        withHttp3Client(sslPort) { connection ->
            val response = connection.request(path = "/boom")
            assertEquals("500", response.status)
            assertNull(response.http3ErrorCode, "an application failure is a response, not a stream error")
        }
    }

    @Test
    fun `an unknown method is rejected with 405`() = runTest {
        createAndStartServer {
            get("/ok") { call.respondText("ok") }
        }

        withHttp3Client(sslPort) { connection ->
            val response = connection.sendRaw {
                method("BOGUS!")
                path("/ok")
                scheme("https")
                authority("localhost:$sslPort")
            }

            assertEquals("405", response.status)
        }
    }

    @Test
    fun `malformed requests are rejected before reaching the application`() = runTest {
        val reachedApplication = CopyOnWriteArrayList<String>()

        createAndStartServer {
            get("/ok") { call.respondText("ok") }
            handle {
                reachedApplication += call.request.origin.uri
                call.respondText("should not happen")
            }
        }

        val malformed = mapOf<String, Http3Headers.() -> Unit>(
            "missing :method" to {
                path("/ok")
                scheme("https")
                authority("localhost:$sslPort")
            },
            "missing :path" to {
                method("GET")
                scheme("https")
                authority("localhost:$sslPort")
            },
            "missing :scheme" to {
                method("GET")
                path("/ok")
                authority("localhost:$sslPort")
            },
            "duplicate :path" to {
                method("GET")
                path("/ok")
                add(":path", "/other")
                scheme("https")
                authority("localhost:$sslPort")
            },
        )

        withHttp3Client(sslPort) { connection ->
            for ((label, build) in malformed) {
                val response = connection.sendRaw(build)

                assertEquals("", response.status, "$label: the codec answers nothing at the HTTP level")
                assertTrue(
                    reachedApplication.isEmpty(),
                    "$label: the request must never reach the application, but it saw $reachedApplication"
                )

                assertEquals(
                    "200",
                    connection.request(path = "/ok").status,
                    "$label: the connection must stay usable"
                )
            }
        }
    }

    /** A reset stream must not disturb its siblings on the same connection. */
    @Test
    fun `resetting one stream leaves the others working`() = runTest {
        createAndStartServer {
            get("/long") {
                delay(3000)
                call.respondText("finished")
            }
            get("/sibling") { call.respondText("sibling-ok") }
        }

        withHttp3Client(sslPort) { connection ->
            val doomed = connection.openStream()
            doomed.sendHeaders(path = "/long", endStream = true)

            val sibling = connection.openStream()
            sibling.sendHeaders(path = "/sibling", endStream = true)

            delay(200)
            doomed.reset()

            assertEquals("sibling-ok", sibling.awaitResponse().bodyText)
            assertEquals("200", connection.request(path = "/sibling").status, "the connection stays usable")
        }
    }

    @Test
    fun `an aborted request does not cancel the handler`() = runTest {
        val outcomes = CopyOnWriteArrayList<String>()

        createAndStartServer {
            get("/long") {
                try {
                    delay(1000)
                    outcomes += "completed"
                    call.respondText("finished")
                } catch (cause: Throwable) {
                    outcomes += "cancelled:${cause::class.simpleName}"
                    throw cause
                }
            }
        }

        withHttp3Client(sslPort) { connection ->
            val doomed = connection.openStream()
            doomed.sendHeaders(path = "/long", endStream = true)

            delay(200)
            doomed.reset()

            // Wait past the handler's own delay so its outcome is settled either way.
            delay(2000)
            assertEquals(
                listOf("completed"),
                outcomes.toList(),
                "the handler ran to completion despite the client abort"
            )
        }
    }

     @Test
    fun `an upgrade attempt answers with a spurious 101`() = runTest {
        // The engine logs the UnsupportedOperationException itself; a plain logger keeps the test
        // base from treating that expected failure as an unhandled one.
        createAndStartServer(log = LoggerFactory.getLogger("io.ktor.test")) {
            get("/upgrade") { call.respond(UnsupportedUpgrade) }
        }

        withHttp3Client(sslPort) { connection ->
            val response = connection.request(path = "/upgrade")

            assertEquals("101", response.status, "no upgrade is possible, yet 101 is reported")
            assertEquals("", response.bodyText)
            assertNull(response.http3ErrorCode)
        }
    }

    /** `push` is documented as unsupported on HTTP/3; it must be a no-op, not a failure. */
    @Test
    fun `push is a silent no-op that does not fail the call`() = runTest {
        createAndStartServer {
            get("/push") {
                call.push("/pushed")
                call.respondText("push-done")
            }
        }

        withHttp3Client(sslPort) { connection ->
            val response = connection.request(path = "/push")
            assertEquals("200", response.status)
            assertEquals("push-done", response.bodyText)
            assertTrue(response.headers["link"] == null || response.headers["link"]!!.isNotEmpty())
        }
    }

    private suspend fun io.ktor.server.test.base.Http3TestConnection.sendRaw(
        build: Http3Headers.() -> Unit
    ): Http3TestResponse {
        val stream = openStream()
        stream.sendRawHeaders(DefaultHttp3Headers().apply(build), endStream = true)
        return stream.awaitResponse(timeout = 10.seconds)
    }

    private companion object {
        private val UnsupportedUpgrade = object : OutgoingContent.ProtocolUpgrade() {
            override suspend fun upgrade(
                input: ByteReadChannel,
                output: ByteWriteChannel,
                engineContext: CoroutineContext,
                userContext: CoroutineContext
            ): Job = error("HTTP/3 must never reach the upgrade handler")
        }
    }
}
