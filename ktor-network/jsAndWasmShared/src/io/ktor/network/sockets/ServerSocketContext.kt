/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.sockets

import io.ktor.network.sockets.nodejs.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.io.*
import kotlin.coroutines.*
import io.ktor.network.sockets.nodejs.Socket as NodejsSocket

internal class ServerSocketContext(
    private val server: Server,
    private val localAddress: SocketAddress?,
    parentContext: Job?
) {
    private val incomingSockets = Channel<Socket>(Channel.UNLIMITED)
    private val serverContext = SupervisorJob(parentContext)

    fun initiate(cont: CancellableContinuation<ServerSocket>) {
        cont.invokeOnCancellation {
            server.close()

            serverContext.cancel()
            incomingSockets.cancel()
        }

        server.on("connection", fun(socket: NodejsSocket) {
            val context = SocketContext(socket, localAddress, serverContext)
            context.initiate(null)
            incomingSockets.trySend(context.createSocket())
        })
        server.on("close", fun() {
            if (cont.isActive) cont.resumeWithException(IOException("Failed to bind"))
            else serverContext.job.cancel("Server closed")
        })
        server.on("error", fun(error: JsError) {
            if (cont.isActive) cont.resumeWithException(IOException("Failed to bind", error.toThrowable()))
            else serverContext.job.cancel("Server failed", error.toThrowable())
        })
        server.on("drop", fun(_: ServerConnectionDrop) {
            // TODO: handle drop?
        })
        server.on("listening", fun() {
            cont.resume(ServerSocketImpl(server.address()!!.toSocketAddress(), serverContext, incomingSockets, server))
        })
        server.listen(ServerListenOptions(localAddress))
    }
}

private class ServerSocketImpl(
    override val localAddress: SocketAddress,
    override val socketContext: Job,
    private val incoming: ReceiveChannel<Socket>,
    private val server: Server
) : ServerSocket {
    override suspend fun accept(): Socket = incoming.receive()

    init {
        socketContext.invokeOnCompletion {
            server.close()
            incoming.cancel()
        }
    }

    override fun close() {
        socketContext.cancel("Server socket closed")
    }
}
