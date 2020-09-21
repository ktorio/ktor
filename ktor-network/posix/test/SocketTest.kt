package io.ktor.network.tests

import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.*
import kotlin.test.*


class SocketTest {

    @Test
    fun testEcho() = runBlocking {
        SelectorManager().use { selector ->
            val tcp = aSocket(selector).tcp()
            val server = tcp.bind("127.0.0.1", 8000)

            val serverConnectionPromise = async {
                server.accept()
            }

            val clientConnection = tcp.connect("127.0.0.1", 8000)
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
    }
}
