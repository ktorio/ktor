/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.server.netty.http3

import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.test.base.EngineTestBase
import io.ktor.server.test.base.withHttp3Client
import io.ktor.utils.io.ExperimentalKtorApi
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalKtorApi::class)
class NettyHttp3ShareWorkGroupTest :
    EngineTestBase<NettyApplicationEngine, NettyApplicationEngine.Configuration>(Netty) {

    init {
        enableSsl = true
        enableHttp3 = true
    }

    override fun configure(configuration: NettyApplicationEngine.Configuration) {
        configuration.shareWorkGroup = true
        configuration.enableHttp3()
    }

    @Test
    fun `blocking user code does not stall other connections with shareWorkGroup`() = runTest {
        val blockEntered = CountDownLatch(1)
        val releaseBlock = CountDownLatch(1)

        createAndStartServer {
            get("/instant") { call.respondText("ok") }
            get("/block") {
                blockEntered.countDown()
                releaseBlock.await() // deliberately blocking user code (e.g. JDBC, file IO)
                call.respondText("done")
            }
        }

        withHttp3Client(sslPort) { blocked ->
            val pending = blocked.openStream()
            pending.sendHeaders(path = "/block", endStream = true)
            assertTrue(blockEntered.await(5, TimeUnit.SECONDS), "server never entered /block")

            try {
                // A brand-new QUIC connection has to complete its handshake and be served while the
                // handler above is parked. All connections of a connector share one DatagramChannel
                // driven by a worker event loop, so this only works if the handler runs elsewhere.
                withHttp3Client(sslPort, connectTimeout = 10.seconds) { other ->
                    assertEquals("ok", other.request(path = "/instant", timeout = 10.seconds).bodyText)
                }
            } finally {
                releaseBlock.countDown()
            }

            assertEquals("done", pending.awaitResponse(timeout = 10.seconds).bodyText)
        }
    }
}
