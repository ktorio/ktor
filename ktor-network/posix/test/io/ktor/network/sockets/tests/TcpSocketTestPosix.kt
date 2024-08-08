/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.sockets.tests

import io.ktor.network.sockets.*
import kotlinx.coroutines.*
import kotlinx.io.*
import platform.posix.*
import kotlin.test.*

class TcpSocketTestPosix {

    @Test
    fun testDescriptorError() = testSockets { selector ->
        val socket = aSocket(selector)
            .tcp()
            .bind(InetSocketAddress("127.0.0.1", 8004))
        val descriptor = (socket as TCPServerSocketNative).selectable.descriptor

        launch {
            // Closing the descriptor here while accept is busy in select, should fail the accept.
            close(descriptor)
        }

        assertFailsWith<IOException> {
            socket.accept()
        }

        socket.close()
    }

    @Test
    fun testDescriptorErrorDoesNotFailOtherSockets() = testSockets { selector ->
        val socket = aSocket(selector)
            .tcp()
            .bind(InetSocketAddress("127.0.0.1", 8005))
        val descriptor = (socket as TCPServerSocketNative).selectable.descriptor

        val socket2 = aSocket(selector)
            .tcp()
            .bind(InetSocketAddress("127.0.0.1", 8006))

        launch {
            launch {
                // Closing the descriptor here while accept is busy in select, should fail the accept.
                // Other sockets (in this case socket2) should not fail.
                close(descriptor)
            }

            assertFailsWith<IOException> {
                socket.accept()
            }

            socket.close()
        }

        // As socket2 should not fail, the timeout is expected.
        withTimeoutOrNull(500) {
            socket2.accept()
        }

        socket.close()
        socket2.close()
    }
}
