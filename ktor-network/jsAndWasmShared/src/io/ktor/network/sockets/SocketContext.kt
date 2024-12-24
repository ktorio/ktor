/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.sockets

import io.ktor.network.sockets.nodejs.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.io.*
import kotlin.coroutines.*
import io.ktor.network.sockets.nodejs.Socket as NodejsSocket

internal class SocketContext(
    private val socket: NodejsSocket,
    private val address: SocketAddress?,
    parentContext: Job?
) {
    private val incomingFrames: Channel<JsBuffer> = Channel(Channel.UNLIMITED)
    private val socketContext = Job(parentContext)

    fun initiate(connectCont: CancellableContinuation<Socket>?) {
        connectCont?.invokeOnCancellation {
            socket.destroy(it?.toJsError())

            socketContext.cancel()
            incomingFrames.cancel()
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

        if (connectCont != null) {
            socket.onConnect {
                connectCont.resume(createSocket())
            }
        }
    }

    // Socket real address could be resolved only after the ` connect ` event.
    // Also, Node.js doesn't give access to unix address from the ` socket ` object,
    // so we need to store it.
    fun createSocket(): Socket = SocketImpl(
        localAddress = when (address) {
            is UnixSocketAddress -> address
            else -> InetSocketAddress(socket.localAddress, socket.localPort)
        },
        remoteAddress = when (address) {
            is UnixSocketAddress -> address
            else -> InetSocketAddress(socket.remoteAddress, socket.remotePort)
        },
        coroutineContext = socketContext,
        incoming = incomingFrames,
        socket = socket
    )
}

private class SocketImpl(
    override val localAddress: SocketAddress,
    override val remoteAddress: SocketAddress,
    override val coroutineContext: CoroutineContext,
    private val incoming: ReceiveChannel<JsBuffer>,
    private val socket: NodejsSocket
) : Socket {
    override val socketContext: Job get() = coroutineContext.job

    init {
        socketContext.invokeOnCompletion {
            socket.destroy(it?.toJsError())
            incoming.cancel(CancellationException("Socket closed", it))
        }
    }

    override fun attachForReading(channel: ByteChannel): WriterJob = writer(Dispatchers.Unconfined, channel = channel) {
        incoming.consumeEach { buffer ->
            channel.writeByteArray(buffer.toByteArray())
            channel.flush()
        }
    }

    override fun attachForWriting(channel: ByteChannel): ReaderJob = reader(Dispatchers.Unconfined, channel = channel) {
        while (true) {
            val result = channel.read { bytes, startIndex, endIndex ->
                socket.write(bytes.toJsBuffer(startIndex, endIndex))
                endIndex - startIndex
            }
            if (result == -1) {
                socket.end()
                break
            }
        }
    }

    override fun close() {
        socketContext.cancel("Socket closed")
    }
}
