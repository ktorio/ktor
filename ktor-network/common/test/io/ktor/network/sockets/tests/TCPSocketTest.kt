/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.sockets.tests

import io.ktor.network.sockets.*
import io.ktor.utils.io.*
import io.ktor.utils.io.CancellationException
import kotlinx.coroutines.*
import kotlinx.io.*
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds

class TCPSocketTest {

    @Test
    fun testEcho() = testSockets { selector ->
        val tcp = aSocket(selector).tcp()
        val server: ServerSocket = tcp.bind("127.0.0.1", port = 0)

        val serverConnectionPromise = async {
            server.accept()
        }

        val clientConnection = tcp.connect("127.0.0.1", port = server.port)
        val serverConnection = serverConnectionPromise.await()

        val clientOutput = clientConnection.openWriteChannel()
        try {
            clientOutput.writeStringUtf8("Hello, world\n")
            clientOutput.flush()
        } finally {
            clientOutput.flushAndClose()
        }

        val serverInput = serverConnection.openReadChannel()
        val message = serverInput.readUTF8Line()
        assertEquals("Hello, world", message)

        val serverOutput = serverConnection.openWriteChannel()
        try {
            serverOutput.writeStringUtf8("Hello From Server\n")
            serverOutput.flush()

            val clientInput = clientConnection.openReadChannel()
            val echo = clientInput.readUTF8Line()

            assertEquals("Hello From Server", echo)
        } finally {
            serverOutput.flushAndClose()
        }

        serverConnection.close()
        clientConnection.close()

        server.close()
    }

    @Test
    fun testEchoOverUnixSockets() = testSockets { selector ->
        if (!supportsUnixDomainSockets()) return@testSockets

        val socketPath = createTempFilePath("ktor-echo-test")
        try {
            val tcp = aSocket(selector).tcp()
            val server = tcp.bind(UnixSocketAddress(socketPath))

            val serverConnectionPromise = async {
                server.accept()
            }

            val clientConnection = tcp.connect(UnixSocketAddress(socketPath))
            val serverConnection = serverConnectionPromise.await()

            val clientOutput = clientConnection.openWriteChannel()
            try {
                clientOutput.writeStringUtf8("Hello, world\n")
                clientOutput.flush()
            } finally {
                clientOutput.flushAndClose()
            }

            val serverInput = serverConnection.openReadChannel()
            val message = serverInput.readUTF8Line()
            assertEquals("Hello, world", message)

            val serverOutput = serverConnection.openWriteChannel()
            try {
                serverOutput.writeStringUtf8("Hello From Server\n")
                serverOutput.flush()

                val clientInput = clientConnection.openReadChannel()
                val echo = clientInput.readUTF8Line()

                assertEquals("Hello From Server", echo)
            } finally {
                serverOutput.flushAndClose()
            }

            serverConnection.close()
            clientConnection.close()

            server.close()
        } finally {
            removeFile(socketPath)
        }
    }

    @Test
    fun testReadFromCancelledSocket() = testSockets { selector ->
        val tcp = aSocket(selector).tcp()
        tcp.bind().use { server ->
            val serverConnection = async {
                server.accept()
            }

            val client: Socket = tcp.connect("127.0.0.1", server.port)
            val readChannel = client.openReadChannel()
            serverConnection.await()

            client.cancel()

            assertFailsWith<CancellationException> {
                readChannel.readByte()
            }
        }
    }

    @Test
    fun testConnectToNonExistingSocket() = testSockets(timeout = 10.seconds) { selector ->
        val tcp = aSocket(selector).tcp()
        val server = tcp.bind("127.0.0.1")
        server.close()

        assertFailsWith<IOException> {
            aSocket(selector)
                .tcp()
                .connect("127.0.0.1", server.port) // trying to connect to a port that was available but now closed
        }
    }

    @Test
    fun testDisconnect() = testSockets { selector ->
        val tcp = aSocket(selector).tcp()
        val server: ServerSocket = tcp.bind("127.0.0.1", port = 0)

        val serverConnectionPromise = async {
            server.accept()
        }

        val clientConnection = tcp.connect("127.0.0.1", port = server.port)

        val clientInput = clientConnection.openReadChannel()
        val clientOutput = clientConnection.openWriteChannel()

        val serverConnection = serverConnectionPromise.await()

        val serverInput = serverConnection.openReadChannel()
        val serverOutput = serverConnection.openWriteChannel()

        // Need to make sure reading from the server is done first, which will suspend because there is nothing to read.
        // Then close the connection from the client side, which should cancel the reading because the socket is disconnected.
        launch {
            delay(100)
            clientConnection.close()
        }

        assertFailsWith<EOFException> {
            serverInput.readByte()
        }
        assertTrue(serverInput.isClosedForRead)

        serverConnection.close()
        server.close()

        clientConnection.socketContext.join()
        serverConnection.socketContext.join()
        server.socketContext.join()

        assertTrue(clientConnection.isClosed)
        assertTrue(clientInput.isClosedForRead)
        assertTrue(clientOutput.isClosedForWrite)
        assertTrue(serverConnection.isClosed)
        assertTrue(serverOutput.isClosedForWrite)
        assertTrue(server.isClosed)
    }

    @Test
    fun testAcceptErrorOnSocketClose() = testSockets { selector ->
        val socket = aSocket(selector)
            .tcp()
            .bind(InetSocketAddress("127.0.0.1", 0))

        launch {
            assertFailsWith<IOException> {
                socket.accept()
            }
        }
        delay(100) // Make sure socket is awaiting connection using ACCEPT

        socket.close()
    }
}
