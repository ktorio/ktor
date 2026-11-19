/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package test.server

import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import kotlinx.coroutines.*

internal class TestTcpServer(
    val port: Int,
    scope: CoroutineScope,
    private val handler: suspend (Socket) -> Unit,
) {
    private val selector = ActorSelectorManager(Dispatchers.IO)

    private val serverSocket = runBlocking { aSocket(selector).tcp().bind(port = port) }

    init {
        scope.launch {
            serverSocket.use { it.serve() }
        }.invokeOnCompletion {
            selector.close()
        }
    }

    private suspend fun ServerSocket.serve() = coroutineScope {
        while (isActive) {
            val socket = try {
                accept()
            } catch (cause: Throwable) {
                if (cause is CancellationException) throw cause
                println("Test server failed to accept: $cause")
                cause.printStackTrace()
                continue
            }

            launch {
                try {
                    socket.use { handler(it) }
                } catch (cause: Throwable) {
                    if (cause is CancellationException) throw cause
                    println("Exception in tcp server: $cause")
                    cause.printStackTrace()
                }
            }
        }
    }
}
