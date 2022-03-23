/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.tls

import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.util.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.*
import kotlin.test.*

class SslTest {

    @OptIn(InternalAPI::class)
    @Test
    fun sslWithoutCloseTest(): Unit = runBlocking {
        SelectorManager().use { selector ->
            aSocket(selector).tcp().connect("www.google.com", port = 443).use { noSecureSocket ->
                val socket = noSecureSocket.ssl(Dispatchers.Default, SslContext().createClientEngine())

                val channel = socket.openWriteChannel()
                channel.apply {
                    writeStringUtf8("GET / HTTP/1.1\r\n")
                    writeStringUtf8("Host: www.google.com\r\n")
                    writeStringUtf8("Connection: close\r\n\r\n")
                    flush()
                }
//                delay(1000)
                println(socket.openReadChannel().readRemaining().readText())
//                socket.openReadChannel().readRemaining()
            }
        }
    }

    @OptIn(InternalAPI::class, DelicateCoroutinesApi::class)
    @Test
    fun sslClientServerTest(): Unit = runBlocking {
        val context = SslContext()

        SelectorManager().use { clientSelector ->
            val tcp = aSocket(clientSelector).tcp()
            tcp.bind().use { serverSocket ->
                val serverJob = GlobalScope.launch {
                    while (true) serverSocket.accept()
                        .ssl(Dispatchers.Default + CoroutineName("SERVER"), context.createServerEngine())
                        .use { socket ->
                            val reader = socket.openReadChannel()
                            val writer = socket.openWriteChannel()
                            repeat(3) {
                                val line = assertNotNull(reader.readUTF8Line())
                                println("SSS: $line")
                                writer.writeStringUtf8("$line\r\n")
                                writer.flush()
                            }
                            delay(2000) //await reading from client socket
                        }
                }

                tcp.connect(serverSocket.localAddress)
                    .ssl(Dispatchers.Default + CoroutineName("CLIENT"), context.createClientEngine())
                    .use { socket ->
                        socket.openWriteChannel().apply {
                            writeStringUtf8("GET / HTTP/1.1\r\n")
                            writeStringUtf8("Host: www.google.com\r\n")
                            writeStringUtf8("Connection: close\r\n")
                            flush()
                        }
                        val reader = socket.openReadChannel()
                        repeat(3) {
                            println("CCC: ${assertNotNull(reader.readUTF8Line())}")
                        }
                    }
                serverJob.cancelAndJoin()
            }
        }
    }

}
