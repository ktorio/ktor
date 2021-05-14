/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.tests.server.netty

import io.ktor.server.engine.*
import io.ktor.server.netty.*
import org.slf4j.*
import java.net.*
import kotlin.test.*

class NettySpecificTest {

    @Test
    fun testNoLeakWithoutStartAndStop() {
        repeat(100000) {
            embeddedServer(Netty, applicationEngineEnvironment { })
        }
    }

    @Test
    fun testStartOnUsedPort() {
        val socket = ServerSocket(0)
        val port = socket.localPort

        val server = embeddedServer(Netty, port) {}

        try {
            server.start(wait = false)
        } catch (_: BindException) {
        }

        assertTrue(server.bootstraps.all { it.config().group().isTerminated })
    }

    @Test
    fun testStartMultipleConnectorsOnUsedPort() {
        val socket = ServerSocket(0)
        val port = socket.localPort

        val socket2 = ServerSocket(0)
        val port2 = socket2.localPort
        val host = "0.0.0.0"

        socket.close()

        try {
            val environment = applicationEngineEnvironment {
                connector {
                    this.port = port
                    this.host = host
                }
                connector {
                    this.port = port2
                    this.host = host
                }
            }

            val server = embeddedServer(Netty, environment)

            try {
                server.start(wait = false)
            } catch (_: BindException) {
            }

            assertTrue(server.bootstraps.all { it.config().group().isTerminated })
        } finally {
            socket2.close()
        }
    }
}
