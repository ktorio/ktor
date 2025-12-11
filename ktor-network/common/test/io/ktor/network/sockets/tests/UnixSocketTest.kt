/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.sockets.tests

import io.ktor.network.sockets.*
import io.ktor.utils.io.*
import kotlinx.coroutines.async
import kotlin.test.Test
import kotlin.test.assertEquals

class UnixSocketTest {

    @Test
    fun testEchoOverUnixSockets() = testSockets { selector ->
        if (!UnixSocketAddress.isSupported()) return@testSockets

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
            val message = serverInput.readLineStrict()
            assertEquals("Hello, world", message)

            val serverOutput = serverConnection.openWriteChannel()
            try {
                serverOutput.writeStringUtf8("Hello From Server\n")
                serverOutput.flush()

                val clientInput = clientConnection.openReadChannel()
                val echo = clientInput.readLineStrict()

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
}
