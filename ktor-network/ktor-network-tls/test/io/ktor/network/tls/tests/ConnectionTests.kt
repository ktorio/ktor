package io.ktor.network.tls.tests

import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.network.tls.*
import io.ktor.network.util.*
import kotlinx.coroutines.*
import kotlinx.coroutines.io.*
import org.junit.*

class ConnectionTests {

    @Test
    fun tlsWithoutCloseTest(): Unit = runBlocking {

        val selectorManager = ActorSelectorManager(Dispatchers.IO)
        val socket = aSocket(selectorManager)
            .tcp()
            .connect("www.google.com", port = 443)
            .tls(Dispatchers.Default)

        val channel = socket.openWriteChannel()

        channel.apply {
            writeStringUtf8("GET / HTTP/1.1\r\n")
            writeStringUtf8("Host: www.google.com\r\n")
            writeStringUtf8("Connection: close\r\n\r\n")
            flush()
        }

        socket.openReadChannel().readRemaining()
        Unit
    }
}
