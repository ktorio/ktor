/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.sockets.tests

import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.test.dispatcher.*
import kotlinx.cinterop.*
import kotlinx.coroutines.*
import kotlinx.io.*
import platform.posix.*
import kotlin.test.*
import kotlin.time.Duration.Companion.minutes

class TcpSocketTestNix {

    @Test
    fun testAcceptedIpv4SocketDisablesNagle() = testAcceptedTcpSocketDisablesNagle("127.0.0.1")

    @Test
    fun testAcceptedIpv6SocketDisablesNagle() = testAcceptedTcpSocketDisablesNagle("::1")

    @OptIn(ExperimentalForeignApi::class)
    private fun testAcceptedTcpSocketDisablesNagle(host: String) = testSockets { selector ->
        val tcp = aSocket(selector).tcp()
        tcp.bind(host, port = 0).use { server ->
            tcp.connect(host, port = server.port).use {
                server.accept().use { connection ->
                    memScoped {
                        val value = alloc<IntVar>()
                        val size = alloc<UIntVar> { this.value = sizeOf<IntVar>().convert() }
                        assertEquals(
                            0,
                            getsockopt(
                                (connection as TCPSocketNative).descriptor,
                                IPPROTO_TCP,
                                TCP_NODELAY,
                                value.ptr,
                                size.ptr
                            )
                        )
                        // Darwin returns a flag mask rather than 1 for an enabled option.
                        assertNotEquals(0, value.value)
                    }
                }
            }
        }
    }

    @Test
    fun testDescriptorClose() = testSuspend {
        val selector = SelectorManager()
        val socket = aSocket(selector)
            .tcp()
            .bind(InetSocketAddress("127.0.0.1", 0))

        val descriptor = (socket as TCPServerSocketNative).descriptor

        socket.close()
        selector.close()

        socket.awaitClosed()

        val isDescriptorValid = fcntl(descriptor, F_GETFL) != -1 || errno != EBADF
        check(!isDescriptorValid) { "Descriptor was not closed" }
    }

    @Test
    fun testDescriptorCloseWithClientAndReadChannel() = testSuspend {
        val selector = SelectorManager()
        val tcp = aSocket(selector).tcp()

        val server = tcp.bind(InetSocketAddress("127.0.0.1", 0))
        val serverDescriptor = (server as TCPServerSocketNative).descriptor

        val serverConnectionPromise = async {
            server.accept()
        }

        val port = (server.localAddress as InetSocketAddress).port
        val clientConnection = tcp.connect("127.0.0.1", port)
        val clientDescriptor = (clientConnection as TCPSocketNative).descriptor

        val serverConnection = serverConnectionPromise.await()
        val serverConnectionDescriptor = (serverConnection as TCPSocketNative).descriptor

        clientConnection.openReadChannel()
        serverConnection.openReadChannel()

        serverConnection.close()
        clientConnection.close()
        server.close()
        selector.close()

        serverConnection.awaitClosed()
        clientConnection.awaitClosed()
        server.awaitClosed()

        val isServerDescriptorValid = fcntl(serverDescriptor, F_GETFL) != -1 || errno != EBADF
        check(!isServerDescriptorValid) { "Server descriptor was not closed" }

        val isServerConnectionDescriptorValid = fcntl(serverConnectionDescriptor, F_GETFL) != -1 || errno != EBADF
        check(!isServerConnectionDescriptorValid) { "Server connection descriptor was not closed" }

        val isClientDescriptorValid = fcntl(clientDescriptor, F_GETFL) != -1 || errno != EBADF
        check(!isClientDescriptorValid) { "Client descriptor was not closed" }
    }

    @Test
    fun testDescriptorError() = testSuspend {
        testSockets(timeout = 2.minutes) { selector ->
            val socket = aSocket(selector)
                .tcp()
                .bind(InetSocketAddress("127.0.0.1", 0))
            val descriptor = (socket as TCPServerSocketNative).descriptor

            launch {
                // Closing the descriptor here while accept is busy in select, should fail the accept.
                close(descriptor)
            }

            assertFailsWith<IOException> {
                socket.accept()
            }

            socket.close()
        }
    }

    @Test
    fun testDescriptorErrorDoesNotFailOtherSockets() = testSockets { selector ->
        val socket = aSocket(selector)
            .tcp()
            .bind(InetSocketAddress("127.0.0.1", 0))
        val descriptor = (socket as TCPServerSocketNative).descriptor

        val socket2 = aSocket(selector)
            .tcp()
            .bind(InetSocketAddress("127.0.0.1", 0))

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
