/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.network.sockets.tests

import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.util.network.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.debug.junit4.*
import org.junit.*
import java.net.*
import kotlin.coroutines.*
import kotlin.io.use
import kotlin.test.*
import kotlin.test.Test

class UDPSocketTest : CoroutineScope {
    private val testJob = Job()
    private val selector = ActorSelectorManager(Dispatchers.Default + testJob)

    @get:Rule
    val timeout = CoroutinesTimeout(1000, cancelOnTimeout = true)

    override val coroutineContext: CoroutineContext
        get() = testJob

    @AfterTest
    fun tearDown() {
        testJob.cancel()
        selector.close()
    }

    @Test
    fun testBroadcastFails(): Unit = runBlocking {
        if (OS_NAME == "win") {
            return@runBlocking
        }

        lateinit var socket: BoundDatagramSocket
        var denied = false
        try {
            socket = aSocket(selector)
                .udp()
                .bind()

            socket.use {
                val datagram = Datagram(
                    packet = buildPacket { writeText("0123456789") },
                    address = NetworkAddress("255.255.255.255", 56700)
                )

                it.send(datagram)
            }
        } catch (cause: SocketException) {
            if (!cause.message.equals("Permission denied", ignoreCase = true)) {
                throw cause
            }

            denied = true
        }

        assertTrue(denied)
        socket.socketContext.join()
        assertTrue(socket.isClosed)
    }

    @Test
    fun testBroadcastSuccessful() = runBlocking {
        val serverSocketCompletable = CompletableDeferred<BoundDatagramSocket>()
        val server = launch {
            aSocket(selector)
                .udp()
                .bind(NetworkAddress("0.0.0.0", 0))
                .use { socket ->
                    serverSocketCompletable.complete(socket)
                    val received = socket.receive()
                    assertEquals("0123456789", received.packet.readText())
                }
        }

        val serverSocket = serverSocketCompletable.await()

        val clientSocket = aSocket(selector)
            .udp()
            .bind {
                broadcast = true
            }
        clientSocket.use { socket ->
            socket.send(
                Datagram(
                    packet = buildPacket { writeText("0123456789") },
                    address = NetworkAddress("255.255.255.255", serverSocket.localAddress.port)
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
    fun testClose(): Unit = runBlocking {
        val socket = aSocket(selector)
            .udp()
            .bind()

        socket.close()

        socket.socketContext.join()
        assertTrue(socket.isClosed)
    }

    @Test
    fun testInvokeOnClose() = runBlocking {
        val socket: BoundDatagramSocket = aSocket(selector)
            .udp()
            .bind()

        var done = 0
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
        assertEquals(1, done)
    }

    @Test
    fun testOutgoingInvokeOnClose() = runBlocking {
        val socket: BoundDatagramSocket = aSocket(selector)
            .udp()
            .bind()

        var done = 0
        socket.outgoing.invokeOnClose {
            done += 1
            assertTrue(it is AssertionError)
        }

        socket.outgoing.close(AssertionError())

        assertEquals(1, done)
        socket.socketContext.join()
        assertTrue(socket.isClosed)
    }

    @Test
    fun testOutgoingInvokeOnCloseWithSocketClose() = runBlocking {
        val socket: BoundDatagramSocket = aSocket(selector)
            .udp()
            .bind()

        var done = 0
        socket.outgoing.invokeOnClose {
            done += 1
        }

        socket.close()

        assertEquals(1, done)

        socket.socketContext.join()
        assertTrue(socket.isClosed)
    }

    @Test
    fun testOutgoingInvokeOnClosed() = runBlocking {
        val socket: BoundDatagramSocket = aSocket(selector)
            .udp()
            .bind()

        socket.outgoing.close(AssertionError())

        var done = 0
        socket.outgoing.invokeOnClose {
            done += 1
            assertTrue(it is AssertionError)
        }

        assertEquals(1, done)

        socket.socketContext.join()
        assertTrue(socket.isClosed)
    }

    @Test
    fun testBind() {
        val socketBuilder: UDPSocketBuilder = aSocket(ActorSelectorManager(Dispatchers.IO)).udp()
        val socket = socketBuilder.bind()
        val port = socket.localAddress.port
        socket.close()

        repeat(1024) {
            try {
                socketBuilder
                    .bind(InetSocketAddress("0.0.0.0", port))
                    .close()
            } catch (_: BindException) {
                // Don't confuse with: Socket Exception: Already bound
            }
        }
    }
}

private val OS_NAME: String
    get() {
        val os = System.getProperty("os.name", "unknown").toLowerCase()
        return when {
            os.contains("win") -> "win"
            os.contains("mac") -> "mac"
            os.contains("nux") -> "unix"
            else -> "unknown"
        }
    }
