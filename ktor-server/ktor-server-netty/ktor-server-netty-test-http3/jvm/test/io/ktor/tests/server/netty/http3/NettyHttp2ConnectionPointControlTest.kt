/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.server.netty.http3

import io.ktor.client.statement.bodyAsText
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.test.base.EngineTestBase
import kotlin.test.Test
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Control for [NettyHttp3ProtocolTest]: the same connection-point code, over HTTP/2 and HTTP/1.1.
 *
 * `HttpMultiplexedConnectionPoint` is shared by HTTP/2 and HTTP/3, so this test establishes that
 * the client address is available there. That is what makes the HTTP/3 gap a defect in the HTTP/3
 * request wiring rather than a limitation of the shared connection point.
 */
class NettyHttp2ConnectionPointControlTest :
    EngineTestBase<NettyApplicationEngine, NettyApplicationEngine.Configuration>(Netty) {

    init {
        enableSsl = true
        enableHttp2 = true
        // This class is the HTTP/1.1 + HTTP/2 half of the comparison, so it opts out of the
        // module-wide http3-only restriction.
        enableHttp3 = false
        http3Only = false
    }

    @Test
    fun `client address is available over HTTP1 and HTTP2`() = runTest {
        createAndStartServer {
            get("/connection-point") { call.respondText(call.describeOrigin()) }
        }

        withUrl("/connection-point") {
            val origin = parseFields(bodyAsText())

            assertNotEquals("unknown", origin["remoteHost"], "client host must be available")
            assertNotEquals("unknown", origin["remoteAddress"], "client address must be available")
            assertNotEquals("0", origin["remotePort"], "client port must be available")
            assertTrue(
                origin["remoteAddress"] in setOf("localhost", "127.0.0.1", "::1", "0:0:0:0:0:0:0:1"),
                "expected a loopback client identity, got ${origin["remoteAddress"]}"
            )
        }
    }

    @Test
    fun `local port is the port the request arrived on over HTTP1 and HTTP2`() = runTest {
        createAndStartServer {
            get("/connection-point") { call.respondText(call.describeOrigin()) }
        }

        withUrl("/connection-point") { requestPort ->
            val origin = parseFields(bodyAsText())
            assertTrue(
                origin["localPort"] == requestPort.toString(),
                "expected localPort $requestPort, got ${origin["localPort"]}"
            )
        }
    }
}
