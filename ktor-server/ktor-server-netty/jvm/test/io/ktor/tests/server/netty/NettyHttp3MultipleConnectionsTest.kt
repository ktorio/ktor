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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

/**
 * Regression test: the HTTP/3 listener must serve more than one QUIC connection.
 *
 * Before the per-connection Http3ServerConnectionHandler fix, the single (non-@Sharable)
 * handler instance passed to QuicServerCodecBuilder.handler(...) made every QUIC connection
 * after the first fail pipeline initialization, so all subsequent handshakes timed out and
 * the listener could only ever serve one connection.
 */
class NettyHttp3MultipleConnectionsTest :
    EngineTestBase<NettyApplicationEngine, NettyApplicationEngine.Configuration>(Netty) {

    init {
        enableSsl = true
    }

    @OptIn(ExperimentalKtorApi::class)
    override fun configure(configuration: NettyApplicationEngine.Configuration) {
        configuration.enableHttp3()
    }

    @Test
    fun `listener serves multiple QUIC connections`() = runTest {
        createAndStartServer {
            application.routing {
                get("/i") { call.respondText("ok") }
            }
        }

        withHttp3Client(sslPort) { first ->
            assertEquals("ok", first.request(path = "/i").bodyText, "the first connection must be served")

            // A second connection, while the first is still open, exercises the handler instance
            // that the regression made unusable. The listener is known to be up by now, so a
            // short timeout keeps a re-regression from stalling for the full connect budget.
            withHttp3Client(sslPort, connectTimeout = SUBSEQUENT_CONNECT_TIMEOUT) { second ->
                assertEquals(
                    "ok",
                    second.request(path = "/i").bodyText,
                    "a second concurrent connection must be accepted"
                )
            }
        }

        // Both previous connections are closed here: a fresh handshake must still be accepted.
        withHttp3Client(sslPort, connectTimeout = SUBSEQUENT_CONNECT_TIMEOUT) { third ->
            assertEquals("ok", third.request(path = "/i").bodyText, "a connection after a close must be accepted")
        }
    }

    private companion object {
        private val SUBSEQUENT_CONNECT_TIMEOUT = 5.seconds
    }
}
