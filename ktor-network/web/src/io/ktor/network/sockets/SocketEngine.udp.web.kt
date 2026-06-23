/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.sockets

import io.ktor.network.selector.*
import io.ktor.network.sockets.nodejs.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.io.*
import org.khronos.webgl.*
import kotlin.coroutines.*

internal actual suspend fun udpConnect(
    selector: SelectorManager,
    remoteAddress: SocketAddress,
    localAddress: SocketAddress?,
    options: SocketOptions.UDPSocketOptions
): ConnectedDatagramSocket {
    require(remoteAddress is InetSocketAddress) { "Only InetSocketAddress is supported for remoteAddress" }
    require(localAddress is InetSocketAddress?) { "Only InetSocketAddress is supported for localAddress" }
    return udpSetup(remoteAddress, localAddress, options)
}

internal actual suspend fun udpBind(
    selector: SelectorManager,
    localAddress: SocketAddress?,
    options: SocketOptions.UDPSocketOptions
): BoundDatagramSocket {
    require(localAddress is InetSocketAddress?) { "Only InetSocketAddress is supported for localAddress" }
    return udpSetup(remoteAddress = null, localAddress, options)
}

private suspend fun udpSetup(
    remoteAddress: InetSocketAddress?, // provided if `connect`
    localAddress: InetSocketAddress?,
    options: SocketOptions.UDPSocketOptions,
): DatagramSocketImpl {
    val nodeDgram = loadNodeDgram()
    return suspendCancellableCoroutine { cont ->
        val socket = nodeDgram.createSocket(DgramCreateSocketOptions(options))

        val socketContext = Job()
        val incomingDatagrams: Channel<Datagram> = Channel(Channel.UNLIMITED)

        cont.invokeOnCancellation {
            socket.close()
            incomingDatagrams.cancel()
            socketContext.cancel()
        }
        socketContext.invokeOnCompletion {
            socket.close()
            incomingDatagrams.cancel()
        }

        socket.onError { error ->
            when (cont.isActive) {
                true -> cont.resumeWithException(IOException("Failed to connect", error.toThrowable()))
                false -> socketContext.cancel("Socket error", error.toThrowable())
            }
        }

        // no need to subscribe via `socket.onClose` as it will be called only when we call `socket.close`

        socket.onMessage { msg, rinfo ->
            @OptIn(ExperimentalUnsignedTypes::class)
            val packet = buildPacket { write(msg.toUByteArray().asByteArray()) }
            incomingDatagrams.trySend(Datagram(packet, rinfo.toSocketAddress()))
        }

        fun createSocket() = DatagramSocketImpl(socketContext, incomingDatagrams, socket, remoteAddress)

        if (remoteAddress != null) {
            socket.onConnect {
                cont.resume(createSocket())
            }
            // `bind` should be called before `connect`
            socket.bind(localAddress?.port, localAddress?.hostname)
            socket.connect(remoteAddress.port, remoteAddress.hostname)
        } else {
            socket.onListening {
                // broadcast should be configured when connected
                socket.setBroadcast(options.broadcast)
                cont.resume(createSocket())
            }
            socket.bind(localAddress?.port, localAddress?.hostname)
        }
    }
}

private class DatagramSocketImpl(
    override val socketContext: CompletableJob,
    private val incomingChannel: Channel<Datagram>,
    private val socket: DgramSocket,
    remote: SocketAddress?,
) : BoundDatagramSocket, ConnectedDatagramSocket {
    override val incoming: ReceiveChannel<Datagram> get() = incomingChannel
    override val outgoing: SendChannel<Datagram> = DatagramSendChannel(socket, this, remote)
    override val localAddress: SocketAddress get() = socket.address().toSocketAddress()
    override val remoteAddress: SocketAddress get() = socket.remoteAddress().toSocketAddress()

    override fun close() {
        socketContext.complete()
        outgoing.close()
    }
}

private class DatagramSendChannel(
    private val dgramSocket: DgramSocket,
    socket: ASocket,
    remote: SocketAddress?,
) : DatagramSendChannelBase(socket, remote) {
    // non-suspend is not supported in Node.js, as it requires a callback - otherwise, the error could be lost
    override fun trySendImpl(element: Datagram): Boolean = false

    override suspend fun sendImpl(element: Datagram) {
        suspendCancellableCoroutine { cont ->
            val target = (element.address as InetSocketAddress).takeIf { remote == null }

            @OptIn(ExperimentalUnsignedTypes::class)
            val msg = element.packet.readByteArray().asUByteArray().toUint8Array()
            dgramSocket.send(
                msg = msg,
                offset = 0,
                length = msg.length,
                port = target?.port,
                address = target?.hostname
            ) { error ->
                if (error != null) {
                    cont.resumeWithException(IOException("Failed to send datagram", error.toThrowable()))
                } else {
                    cont.resume(Unit)
                }
            }
        }
    }
}
