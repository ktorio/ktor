/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.sockets

import io.ktor.network.selector.*
import io.ktor.utils.io.*

@OptIn(ExperimentalKtorApi::class)
internal actual suspend fun udpConnectBufferReceiver(
    selector: SelectorManager,
    remoteAddress: SocketAddress,
    localAddress: SocketAddress?,
    options: SocketOptions.UDPSocketOptions
): ConnectedDatagramBufferSocket {
    val socket = udpConnect(
        selector, remoteAddress, localAddress, options
    ) as DatagramSocketNative

    val bound = BoundDatagramBufferSocket(
        receiver = DatagramBufferReceiver { buffer, offset, length ->
            socket.receiveInto(buffer, offset, length)
        },
        localAddress = { socket.localAddress },
        send = { datagram -> socket.outgoing.send(datagram) },
        close = { socket.close() }
    )

    return ConnectedDatagramBufferSocket(
        bound = bound,
        remoteAddress = { socket.remoteAddress }
    )
}
