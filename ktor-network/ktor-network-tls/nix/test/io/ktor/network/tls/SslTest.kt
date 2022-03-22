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

}
