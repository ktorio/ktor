/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.sockets

import io.ktor.network.selector.*
import io.ktor.network.sockets.nodejs.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.io.*
import org.khronos.webgl.*
import kotlin.coroutines.*
import io.ktor.network.sockets.nodejs.Socket as NodejsSocket

internal actual suspend fun tcpConnect(
    selector: SelectorManager,
    remoteAddress: SocketAddress,
    socketOptions: SocketOptions.TCPClientSocketOptions
): Socket {
    val nodeNet = loadNodeNet()
    return suspendCancellableCoroutine { cont ->
        val socket = nodeNet.createConnection(CreateConnectionOptions(remoteAddress, socketOptions))
        tcpSocketSetup(
            socket = socket,
            serverAddress = remoteAddress,
            parentContext = null,
            connectCont = cont
        )
    }
}

private fun tcpSocketSetup(
    socket: NodejsSocket,
    serverAddress: SocketAddress?, // remote or server address
    parentContext: Job?,
    connectCont: CancellableContinuation<Socket>?
): Socket? {
    val socketContext = Job(parentContext)
    val incomingFrames: Channel<Uint8Array> = Channel(Channel.UNLIMITED)

    connectCont?.invokeOnCancellation {
        socket.destroy(it?.toJsError())

        socketContext.cancel()
        incomingFrames.cancel()
    }
    socketContext.invokeOnCompletion {
        socket.destroy(it?.toJsError())
    }
    socket.onError { error ->
        when (connectCont?.isActive) {
            true -> connectCont.resumeWithException(IOException("Failed to connect", error.toThrowable()))
            else -> socketContext.job.cancel("Socket error", error.toThrowable())
        }
    }
    socket.onTimeout {
        when (connectCont?.isActive) {
            true -> connectCont.resumeWithException(SocketTimeoutException("timeout"))
            else -> socketContext.job.cancel("Socket timeout", SocketTimeoutException("timeout"))
        }
    }
    socket.onEnd {
        incomingFrames.close()
    }
    socket.onClose {
        socketContext.job.cancel("Socket closed")
    }
    socket.onData { data ->
        incomingFrames.trySend(data)
    }

    // Socket real address could be resolved only after the ` connect ` event.
    // Also, Node.js doesn't give access to unix address from the ` socket ` object,
    // so we need to store it.
    fun createSocket(): Socket = SocketImpl(
        localAddress = when (serverAddress) {
            is UnixSocketAddress -> serverAddress
            else -> InetSocketAddress(socket.localAddress, socket.localPort)
        },
        remoteAddress = when (serverAddress) {
            is UnixSocketAddress -> serverAddress
            else -> InetSocketAddress(socket.remoteAddress, socket.remotePort)
        },
        coroutineContext = socketContext,
        incoming = incomingFrames,
        socket = socket
    )

    // if `cont` is provided -> it's `tcpConnect`, otherwise, it's `tcpAccept`
    return if (connectCont != null) {
        socket.onConnect {
            connectCont.resume(createSocket())
        }
        null
    } else {
        createSocket()
    }
}

internal actual suspend fun tcpBind(
    selector: SelectorManager,
    localAddress: SocketAddress?,
    socketOptions: SocketOptions.AcceptorOptions
): ServerSocket {
    val nodeNet = loadNodeNet()
    return suspendCancellableCoroutine { cont ->
        val server = nodeNet.createServer(CreateServerOptions {})

        val serverContext = SupervisorJob()
        val incomingSockets = Channel<Socket>(Channel.UNLIMITED)

        cont.invokeOnCancellation {
            server.close()

            serverContext.cancel()
            incomingSockets.cancel()
        }

        server.onClose {
            when (cont.isActive) {
                true -> cont.resumeWithException(IOException("Failed to bind"))
                else -> serverContext.job.cancel("Server closed")
            }
        }
        server.onError { error ->
            when (cont.isActive) {
                true -> cont.resumeWithException(IOException("Failed to bind", error.toThrowable()))
                else -> serverContext.job.cancel("Server failed", error.toThrowable())
            }
        }

        server.onConnection { socket ->
            incomingSockets.trySend(
                tcpSocketSetup(
                    socket = socket,
                    serverAddress = localAddress,
                    parentContext = serverContext,
                    connectCont = null
                )!!
            )
        }
        server.onListening {
            cont.resume(
                ServerSocketImpl(
                    localAddress = server.address()!!.toSocketAddress(),
                    socketContext = serverContext,
                    incoming = incomingSockets,
                    server = server
                )
            )
        }
        server.listen(ServerListenOptions(localAddress))
    }
}

private class SocketImpl(
    override val localAddress: SocketAddress,
    override val remoteAddress: SocketAddress,
    override val coroutineContext: CoroutineContext,
    private val incoming: ReceiveChannel<Uint8Array>,
    private val socket: NodejsSocket
) : SocketBase(coroutineContext), Socket {

    override fun attachForReadingImpl(channel: ByteChannel): WriterJob =
        writer(Dispatchers.Unconfined, channel = channel) {
            incoming.consumeEach { buffer ->
                @OptIn(ExperimentalUnsignedTypes::class)
                channel.writeByteArray(buffer.toUByteArray().asByteArray())
                channel.flush()
            }
        }

    override fun attachForWritingImpl(channel: ByteChannel): ReaderJob =
        reader(Dispatchers.Unconfined, channel = channel) {
            while (true) {
                val result = channel.read { bytes, startIndex, endIndex ->
                    @OptIn(ExperimentalUnsignedTypes::class)
                    socket.write(bytes.asUByteArray().toUint8Array().subarray(startIndex, endIndex))
                    endIndex - startIndex
                }
                if (result == -1) {
                    val endCompleted = CompletableDeferred<Unit>()
                    socket.end { endCompleted.complete(Unit) }
                    endCompleted.await()
                    break
                }
            }
        }

    override fun actualClose(): Throwable? {
        socket.destroy(null)
        return null
    }
}

private class ServerSocketImpl(
    override val localAddress: SocketAddress,
    override val socketContext: CompletableJob,
    private val incoming: Channel<Socket>,
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
        incoming.close(IOException("Server socket closed"))
        socketContext.complete()
    }
}
