/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.sockets.tests

import io.ktor.network.sockets.*
import io.ktor.utils.io.core.*
import kotlinx.atomicfu.*
import kotlinx.coroutines.*
import kotlin.test.*

class UDPSocketTest {

    private val done = atomic(0)

    @Test
    fun testBroadcastFails(): Unit = testSockets { dispatcher ->
        if (isJvmWindows()) {
            return@testSockets
        }

        lateinit var socket: BoundDatagramSocket
        var denied = false
        try {
            socket = dispatcher
                .udp()
                .bind()

            socket.use {
                val datagram = Datagram(
                    packet = buildPacket { writeText("0123456789") },
                    address = InetSocketAddress("255.255.255.255", 56700)
                )

                it.send(datagram)
            }
        } catch (cause: Exception) {
            if (cause.message?.contains("Permission denied", ignoreCase = true) != true) {
                throw cause
            }

            denied = true
        }

        assertTrue(denied)
        socket.socketContext.join()
        assertTrue(socket.isClosed)
    }

    @Test
    fun testBroadcastSuccessful() = testSockets { dispatcher ->
        val serverSocketCompletable = CompletableDeferred<BoundDatagramSocket>()
        val server = launch {
            dispatcher
                .udp()
                .bind(InetSocketAddress("0.0.0.0", 0))
                .use { socket ->
                    serverSocketCompletable.complete(socket)
                    val received = socket.receive()
                    assertEquals("0123456789", received.packet.readText())
                }
        }

        val serverSocket = serverSocketCompletable.await()

        val clientSocket = dispatcher
            .udp()
            .bind {
                broadcast = true
            }
        clientSocket.use { socket ->
            val inetSocketAddress = serverSocket.localAddress as? InetSocketAddress
                ?: error("Should be an inet address")

            socket.send(
                Datagram(
                    packet = buildPacket { writeText("0123456789") },
                    address = InetSocketAddress("255.255.255.255", inetSocketAddress.port)
                )
            )
        }

        server.join()

        serverSocket.socketContext.join()
        assertTrue(serverSocket.isClosed)

        clientSocket.socketContext.join()
        assertTrue(clientSocket.isClosed)
    }

    @Test
    fun testClose(): Unit = testSockets { dispatcher ->
        val socket = dispatcher
            .udp()
            .bind()

        socket.close()

        socket.socketContext.join()
        assertTrue(socket.isClosed)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun testInvokeOnClose() = testSockets { dispatcher ->
        val socket: BoundDatagramSocket = dispatcher
            .udp()
            .bind()

        socket.outgoing.invokeOnClose {
            done += 1
        }

        assertFailsWith<IllegalStateException> {
            socket.outgoing.invokeOnClose {
                done += 2
            }
        }

        socket.close()
        socket.close()

        socket.socketContext.join()
        assertTrue(socket.isClosed)
        assertEquals(1, done.value)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun testOutgoingInvokeOnClose() = testSockets { dispatcher ->
        val socket: BoundDatagramSocket = dispatcher
            .udp()
            .bind()

        socket.outgoing.invokeOnClose {
            done += 1
            assertTrue(it is AssertionError)
        }

        socket.outgoing.close(AssertionError())

        assertEquals(1, done.value)
        socket.socketContext.join()
        assertTrue(socket.isClosed)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun testOutgoingInvokeOnCloseWithSocketClose() = testSockets { dispatcher ->
        val socket: BoundDatagramSocket = dispatcher
            .udp()
            .bind()

        socket.outgoing.invokeOnClose {
            done += 1
        }

        socket.close()

        assertEquals(1, done.value)

        socket.socketContext.join()
        assertTrue(socket.isClosed)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun testOutgoingInvokeOnClosed() = testSockets { dispatcher ->
        val socket: BoundDatagramSocket = dispatcher
            .udp()
            .bind()

        socket.outgoing.close(AssertionError())

        socket.outgoing.invokeOnClose {
            done += 1
            assertTrue(it is AssertionError)
        }

        assertEquals(1, done.value)

        socket.socketContext.join()
        assertTrue(socket.isClosed)
    }

    @Test
    fun testSendReceive(): Unit = testSockets { dispatcher ->
        dispatcher
            .udp()
            .bind(InetSocketAddress("127.0.0.1", 8000)) {
                reuseAddress = true
            }
            .use { socket ->
                val localAddress = socket.localAddress as? InetSocketAddress
                assertEquals(8000, localAddress?.port)

                // Send messages to localhost
                launch {
                    val address = InetSocketAddress("127.0.0.1", 8000)
                    repeat(10) {
                        val bytePacket = buildPacket { append("hello") }
                        val data = Datagram(bytePacket, address)
                        socket.send(data)
                    }
                }

                // Receive messages from localhost
                repeat(10) {
                    val incoming = socket.receive()
                    assertEquals("hello", incoming.packet.readText())
                }
            }
    }
}

expect fun isJvmWindows(): Boolean
