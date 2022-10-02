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

expect val Dispatchers.IOBridge: CoroutineDispatcher

class TlsClientSocketTest {

    @Test
    fun testGoogleWithoutClose(): Unit = runBlocking {
        if (PlatformUtils.IS_DARWIN) return@runBlocking
        SelectorManager(Dispatchers.IOBridge).use { selector ->
            aSocket(selector)
                .tcp()
                .connect(hostname = "www.google.com", port = 443)
                .tls(Dispatchers.IOBridge) {
                    authentication({ "".toCharArray() }) {} //forces using of SSLEngine
                }
                .use { socket ->
                    socket.openWriteChannel().run {
                        writeStringUtf8("GET / HTTP/1.1\r\n")
                        writeStringUtf8("Host: www.google.com\r\n")
                        writeStringUtf8("Connection: close\r\n\r\n")
                        flush()
                    }
                    println(socket.openReadChannel().readRemaining().readText())
                }
        }
    }
}
