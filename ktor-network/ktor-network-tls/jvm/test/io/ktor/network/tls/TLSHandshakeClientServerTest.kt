/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.tls

import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import kotlinx.coroutines.*
import kotlin.test.*

class TLSHandshakeClientServerTest {

    @Test
    fun tlsHandshake() = runBlocking {
        val selectorManager = ActorSelectorManager(Dispatchers.IO)
        val serverSocket = aSocket(selectorManager)
            .tcp()
        val socket = aSocket(selectorManager)
            .tcp()
            .connect("0.0.0.0", port = 0)
            .tls(Dispatchers.Default)
        val channel = socket.openWriteChannel()
    }

}
