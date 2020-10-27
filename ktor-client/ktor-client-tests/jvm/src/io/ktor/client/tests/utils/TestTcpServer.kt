/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests.utils

import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.network.sockets.Socket
import kotlinx.coroutines.*
import java.net.*
import kotlin.coroutines.*

internal class TestTcpServer(val port: Int, handler: suspend (Socket) -> Unit) : CoroutineScope {
    private val selector = ActorSelectorManager(Dispatchers.IO)
    override val coroutineContext: CoroutineContext

    init {
        val server = aSocket(selector).tcp().bind(InetSocketAddress(port))

        coroutineContext = GlobalScope.launch {
            while (isActive) {
                val socket = server.accept()

                try {
                    socket.use { handler(it) }
                } catch (cause: Throwable) {
                    println("Exception in tcp server: $cause")
                    cause.printStackTrace()
                }
            }
        }.apply {
            invokeOnCompletion {
                server.close()
            }
        }
    }

    public fun close() {
        coroutineContext.cancel()
    }
}
