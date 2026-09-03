/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.server.netty.http3

import io.ktor.server.application.Application
import io.ktor.server.application.serverConfig
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.applicationEnvironment
import io.ktor.server.engine.embeddedServer
import io.ktor.server.engine.sslConnector
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.test.base.EngineTestBase
import io.ktor.server.test.base.withHttp3Client
import io.ktor.utils.io.ExperimentalKtorApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalKtorApi::class)
class NettyHttp3LifecycleTest :
    EngineTestBase<NettyApplicationEngine, NettyApplicationEngine.Configuration>(Netty) {

    init {
        enableSsl = true
        enableHttp3 = true
    }

    override fun configure(configuration: NettyApplicationEngine.Configuration) {
        configuration.enableHttp3()
    }

    private fun http3Server(
        configureEngine: NettyApplicationEngine.Configuration.() -> Unit,
        module: Application.() -> Unit = {},
    ): EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration> {
        val properties = serverConfig(applicationEnvironment { }) { module(module) }
        return embeddedServer(Netty, properties, configureEngine)
    }

    private fun NettyApplicationEngine.Configuration.testSslConnector(sslConnectorPort: Int) {
        sslConnector(
            keyStore,
            "mykey",
            { "changeit".toCharArray() },
            { "changeit".toCharArray() }
        ) {
            port = sslConnectorPort
            keyStorePath = keyStoreFile.absoluteFile
        }
    }

    /**
     * The server-side half of the drain works: a graceful shutdown lets an in-flight handler run to
     * completion, and `respond` returns successfully. HTTP/3 sockets live on the worker event group,
     * so it is that group's shutdown that governs this.
     */
    @Test
    fun `an in flight handler completes during a graceful shutdown`() = runTest {
        val marks = CopyOnWriteArrayList<String>()

        val server = createAndStartServer {
            get("/slow") {
                marks += "entered"
                delay(700)
                call.respondText("drained")
                marks += "responded"
            }
        }

        withHttp3Client(sslPort) { connection ->
            val stream = connection.openStream()
            stream.sendHeaders(path = "/slow", endStream = true)
            delay(200) // let the handler start before the shutdown begins

            coroutineScope {
                val stopping = async(Dispatchers.IO) {
                    server.stop(3000, 5000, TimeUnit.MILLISECONDS)
                }
                stopping.await()
            }

            assertEquals(
                listOf("entered", "responded"),
                marks.toList(),
                "the handler must be drained, not cancelled, during a graceful shutdown"
            )
        }
    }

    /**
     * The response produced by that drained handler never reaches the client.
     *
     * `stop()` waits out the grace period and the handler completes — `respondText` returns without
     * throwing — but no HEADERS or DATA frame arrives. So the work is done and the result is
     * discarded, while the application has every reason to believe it answered.
     *
     * **Not HTTP/3-specific.** The same scenario over HTTP/1.1 on this engine also completes the
     * handler (`[entered, responded]`) without delivering the response; the HTTP/1.1 client gets a
     * premature connection close. What *is* specific to HTTP/3 is that the client receives no signal
     * at all — no close, no error — so it hangs until its own timeout instead of failing fast.
     */
    @Test
    @Ignore("HTTP/3 discards the in-flight response during a graceful shutdown; see the KDoc above")
    fun `graceful shutdown delivers the in flight response`() = runTest {
        val server = createAndStartServer {
            get("/slow") {
                delay(700)
                call.respondText("drained")
            }
        }

        withHttp3Client(sslPort) { connection ->
            val stream = connection.openStream()
            stream.sendHeaders(path = "/slow", endStream = true)
            delay(200)

            coroutineScope {
                val stopping = async(Dispatchers.IO) {
                    server.stop(3000, 5000, TimeUnit.MILLISECONDS)
                }

                assertEquals(
                    "drained",
                    stream.awaitResponse(timeout = 20.seconds).bodyText,
                    "the in-flight response must reach the client"
                )
                stopping.await()
            }
        }
    }

    /**
     * `stop()` has to release the UDP sockets, not just the TCP ones — otherwise a redeploy on the
     * same port fails until the old process' sockets are collected.
     */
    @Test
    fun `stopping releases the UDP socket so the port can be rebound immediately`() = runTest {
        val first = http3Server(
            configureEngine = {
                testSslConnector(sslPort)
                enableHttp3()
            },
            module = { routing { get("/which") { call.respondText("first") } } }
        )

        first.start(wait = false)
        withHttp3Client(sslPort) { connection ->
            assertEquals("first", connection.request(path = "/which").bodyText)
        }
        first.stop(0, 1000, TimeUnit.MILLISECONDS)

        val second = http3Server(
            configureEngine = {
                testSslConnector(sslPort)
                enableHttp3()
            },
            module = { routing { get("/which") { call.respondText("second") } } }
        )

        try {
            second.start(wait = false)
            withHttp3Client(sslPort) { connection ->
                assertEquals(
                    "second",
                    connection.request(path = "/which").bodyText,
                    "a replacement server must be able to bind the released UDP port"
                )
            }
        } finally {
            second.stop(0, 1000, TimeUnit.MILLISECONDS)
        }
    }
}

@OptIn(ExperimentalKtorApi::class)
class NettyHttp3IdleTimeoutTest :
    EngineTestBase<NettyApplicationEngine, NettyApplicationEngine.Configuration>(Netty) {

    init {
        enableSsl = true
        enableHttp3 = true
    }

    override fun configure(configuration: NettyApplicationEngine.Configuration) {
        configuration.enableHttp3 { quicMaxIdleTimeout = 1.seconds }
    }

    @Test
    fun `an idle connection is closed and a new one still works`() = runTest {
        createAndStartServer {
            get("/ping") { call.respondText("pong") }
        }

        withHttp3Client(sslPort) { idle ->
            assertEquals("pong", idle.request(path = "/ping").bodyText)

            // Idle for longer than the server's 1s budget.
            delay(2500)

            val afterIdle = runCatching { idle.request(path = "/ping", timeout = 5.seconds) }
            assertTrue(
                afterIdle.isFailure || afterIdle.getOrNull()?.status != "200",
                "the server must have dropped the idle connection, but the request succeeded"
            )
        }

        // The listener itself is unaffected: a fresh connection is served normally.
        withHttp3Client(sslPort) { fresh ->
            assertEquals("pong", fresh.request(path = "/ping").bodyText)
        }
    }
}
