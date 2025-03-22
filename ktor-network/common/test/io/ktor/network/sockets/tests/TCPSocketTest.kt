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

private val testSize = listOf(
    0,
    1, // small edge cases
    4 * 1024 - 1,
    4 * 1024,
    4 * 1024 + 1, // ByteChannel edge cases
    10 * 4 * 1024, // 4 chunks
    10 * 4 * (1024 + 8), // 4 chunks
    8 * 1024 * 1024 // big
)

private val testArrays = testSize.map {
    makeArray(it)
}

private fun makeArray(size: Int): ByteArray = ByteArray(size) { it.toByte() }

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
    fun testEchoByteArray() = testSockets { selector ->
        val tcp = aSocket(selector).tcp()
        val server: ServerSocket = tcp.bind("127.0.0.1", port = 0)

        val acceptJob = launch {
            while (true) {
                val serverConnection = server.accept()
                launch {
                    val serverInput = serverConnection.openReadChannel()
                    val serverOutput = serverConnection.openWriteChannel()

                    serverOutput.writeSource(serverInput.readRemaining())
                    serverOutput.flushAndClose()

                    serverConnection.close()
                }
            }
        }

        testArrays.forEach { content ->
            val clientConnection = tcp.connect("127.0.0.1", port = server.port)
            val clientInput = clientConnection.openReadChannel()
            val clientOutput = clientConnection.openWriteChannel()

            clientOutput.writeByteArray(content)
            clientOutput.flushAndClose()

            val response = clientInput.readRemaining().readByteArray()
            assertTrue(
                response.contentEquals(content),
                "Test fail with size: ${content.size}, actual size: ${response.size}"
            )

            clientConnection.close()
        }

        acceptJob.cancelAndJoin()
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
            val serverConnectionPromise = async {
                server.accept()
            }

            val client: Socket = tcp.connect("127.0.0.1", server.port)
            val readChannel = client.openReadChannel()
            val serverConnection = serverConnectionPromise.await()

            client.cancel()

            assertFailsWith<CancellationException> {
                readChannel.readByte()
            }

            client.close()
            serverConnection.close()
        }
    }

    @Test
    fun testConnectToNonExistingSocket() = testSockets(timeout = 10.seconds) { selector ->
        val tcp = aSocket(selector).tcp()
        val server = tcp.bind("127.0.0.1")
        server.close()
        server.awaitClosed()

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

        clientConnection.awaitClosed()
        serverConnection.awaitClosed()
        server.awaitClosed()

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

        val acceptJob = launch {
            // The accept call should fail with IOException/PosixException because the socket was closed,
            // but it must not be a bad descriptor error caused by closed descriptor in select call.
            try {
                socket.accept()
            } catch (exception: IOException) {
                assertFalse("Bad descriptor" in exception.message.orEmpty())
            } catch (exception: Exception) {
                assertTrue(exception.isPosixException())
            }
        }
        delay(100) // Make sure socket is awaiting connection using ACCEPT

        socket.close()
        socket.awaitClosed()
        acceptJob.join()
    }

    @Test
    fun testAcceptErrorOnImmediateSocketClose() = testSockets { selector ->
        val socket = aSocket(selector)
            .tcp()
            .bind(InetSocketAddress("127.0.0.1", 0))

        val acceptJob = launch(start = CoroutineStart.UNDISPATCHED) {
            // The accept call should fail with IOException because the socket was closed,
            // but it must not be a bad descriptor error.
            val exception = assertFailsWith<IOException> {
                socket.accept()
            }
            assertFalse("Bad descriptor" in exception.message.orEmpty())
        }

        socket.close()
        socket.awaitClosed()
        acceptJob.join()
    }

    @Test
    fun testBindMultipleTimes() = testSockets { selector ->
        var port = 0
        repeat(10) {
            val socket = aSocket(selector)
                .tcp()
                .bind("127.0.0.1", port)
            if (port == 0) {
                port = socket.port
            }
            socket.close()
            socket.awaitClosed()
        }
    }
}
