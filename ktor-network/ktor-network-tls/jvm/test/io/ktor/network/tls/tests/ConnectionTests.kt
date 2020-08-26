/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.tls.tests

import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.network.sockets.ServerSocket
import io.ktor.network.tls.*
import io.ktor.network.tls.certificates.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlinx.coroutines.debug.junit4.*
import org.junit.*
import java.io.*
import java.net.*
import javax.net.ssl.*

class ConnectionTests {

    @get:Rule
    val timeout = CoroutinesTimeout.seconds(20)

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

    @Test
    fun certificateTest(): Unit = runBlocking {
        val keyStore = generateCertificate(
            File.createTempFile("test", "certificate"),
            algorithm = "SHA256withRSA",
            keySizeInBits = 4096
        )

        val factory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        factory.init(keyStore, "changeit".toCharArray())

        val socket = aSocket(ActorSelectorManager(Dispatchers.IO)).tcp()
            .connect(InetSocketAddress("chat.freenode.net", 6697))
            .tls(Dispatchers.IO) {
                addKeyStore(keyStore, "changeit".toCharArray())
            }


        val input = socket.openReadChannel()
        val output = socket.openWriteChannel(autoFlush = true)
        output.close()
        socket.close()
    }


    @Test
    fun tlsHandshakeClientRequestSize(): Unit = runBlocking {
        val keyStore = generateCertificate(
            File.createTempFile("test", "certificate"),
            algorithm = "SHA256withRSA",
            keySizeInBits = 4096
        )

        val factory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        val password: CharArray? = null
        factory.init(keyStore, password)

        val server: ServerSocket = aSocket(ActorSelectorManager(Dispatchers.IO)).tcp().configure {

        }
            .bind()
            .tls(Dispatchers.IO) {
                addKeyStore(keyStore, "changeit".toCharArray())
            }

        val client = server.accept()
            .tls(Dispatchers.IO) {
                addKeyStore(keyStore, "changeit".toCharArray())
            }

        val input = client.openReadChannel()
        val output = client.openWriteChannel(autoFlush = true)
        output.close()
        client.close()
        server.close()
    }
}
