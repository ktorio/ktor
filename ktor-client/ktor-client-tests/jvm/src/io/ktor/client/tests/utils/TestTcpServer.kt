/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests.utils

import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.network.sockets.Socket
import kotlinx.coroutines.*
import java.io.*
import java.net.*
import kotlin.coroutines.*

@OptIn(DelicateCoroutinesApi::class)
internal class TestTcpServer(val port: Int, handler: suspend (Socket) -> Unit) : CoroutineScope, Closeable {
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

    override fun close() { coroutineContext.cancel() }
}
