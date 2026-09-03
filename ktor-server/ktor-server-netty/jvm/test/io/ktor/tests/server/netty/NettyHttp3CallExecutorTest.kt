/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.server.netty

import io.ktor.server.application.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.test.base.*
import io.ktor.utils.io.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Regression test: application handler code must not run on the QUIC event loop.
 *
 * Without dispatching calls to an executor pinned from the call event group (as HTTP/1 and
 * HTTP/2 do since KTOR-9542), user handler code runs on the QUIC event loop. All QUIC
 * connections of a connector share a single DatagramChannel driven by one event loop, so any
 * blocking user code (JDBC, file IO) stalls the entire HTTP/3 listener, including QUIC
 * handshakes of unrelated new connections.
 */
class NettyHttp3CallExecutorTest :
    EngineTestBase<NettyApplicationEngine, NettyApplicationEngine.Configuration>(Netty) {

    init {
        enableSsl = true
    }

    @OptIn(ExperimentalKtorApi::class)
    override fun configure(configuration: NettyApplicationEngine.Configuration) {
        configuration.enableHttp3()
    }

    @Test
    fun `blocking user code on one HTTP3 connection must not stall other connections`() = runTest {
        val blockEntered = CountDownLatch(1)
        val releaseBlock = CountDownLatch(1)

        createAndStartServer {
            application.routing {
                get("/instant") { call.respondText("ok") }
                get("/block") {
                    blockEntered.countDown()
                    releaseBlock.await() // deliberately blocking user code (e.g. JDBC, file IO)
                    call.respondText("done")
                }
            }
        }

        withHttp3Client(sslPort) { connectionA ->
            // fire GET /block on connection A without awaiting the response
            val pending = connectionA.openStream()
            pending.sendHeaders(path = "/block", endStream = true)
            assertTrue(blockEntered.await(5, TimeUnit.SECONDS), "server never entered /block")

            // While /block is still parked on releaseBlock above, a brand-new QUIC connection
            // (fresh handshake) must still be served. This only succeeds if the blocking
            // handler runs off the shared QUIC event loop, since all connections of a connector
            // share a single DatagramChannel driven by one event loop.
            withHttp3Client(sslPort) { connectionB ->
                val response = connectionB.request(path = "/instant")
                assertEquals("ok", response.bodyText)
            }

            releaseBlock.countDown()
            assertEquals("done", pending.awaitResponse().bodyText)
        }
    }
}
