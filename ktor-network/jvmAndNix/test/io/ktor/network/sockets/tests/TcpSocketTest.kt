/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.sockets.tests

import io.ktor.network.sockets.*
import io.ktor.util.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlin.test.*

class TcpSocketTest {

    @OptIn(InternalAPI::class)
    @Test
    fun testEcho() = testSockets { dispatcher ->
        val tcp = dispatcher.tcp()
        val server = tcp.bind("localhost", 8000)

        val serverConnectionPromise = async {
            server.accept()
        }

        val clientConnection = tcp.connect("localhost", 8000)
        val serverConnection = serverConnectionPromise.await()

        val clientOutput = clientConnection.openWriteChannel()
        try {
            clientOutput.writeStringUtf8("Hello, world\n")
            clientOutput.flush()
        } finally {
            clientOutput.close()
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
            serverOutput.close()
        }

        serverConnection.close()
        clientConnection.close()

        server.close()
    }

    @Test
    fun testEchoOverUnixSockets() = testSockets { dispatcher ->
        if (!supportsUnixDomainSockets()) return@testSockets

        val socketPath = createTempFilePath("ktor-echo-test")

        val tcp = dispatcher.tcp()
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
            clientOutput.close()
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
            serverOutput.close()
        }

        serverConnection.close()
        clientConnection.close()

        server.close()

        removeFile(socketPath)
    }
}
