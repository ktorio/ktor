/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package test.server

import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import kotlinx.coroutines.*
import java.io.*
import kotlin.coroutines.*

@OptIn(DelicateCoroutinesApi::class)
internal class TestTcpServer(val port: Int, handler: suspend (Socket) -> Unit) : CoroutineScope, Closeable {
    private val selector = ActorSelectorManager(Dispatchers.IO)
    override val coroutineContext: CoroutineContext

    init {
        var server: ServerSocket? = null

        coroutineContext = GlobalScope.launch {
            server = aSocket(selector).tcp().bind(port = port)
            while (isActive) {
                val socket = try {
                    server?.accept()
                } catch (cause: Throwable) {
                    println("Test server is fail to accept: $cause")
                    cause.printStackTrace()
                    continue
                }

                launch {
                    try {
                        socket?.use { handler(it) }
                    } catch (cause: Throwable) {
                        println("Exception in tcp server: $cause")
                        cause.printStackTrace()
                    }
                }
            }
        }.apply {
            invokeOnCompletion {
                server?.close()
            }
        }
    }

    override fun close() {
        coroutineContext.cancel()
    }
}
