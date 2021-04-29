/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.network.sockets

import io.ktor.network.selector.*
import io.ktor.util.network.*

internal actual suspend fun connect(
    selector: SelectorManager,
    networkAddress: NetworkAddress,
    socketOptions: SocketOptions.TCPClientSocketOptions
): Socket = selector.buildOrClose({ openSocketChannel() }) {
    assignOptions(socketOptions)
    nonBlocking()

    SocketImpl(this, socket()!!, selector, socketOptions).apply {
        connect(networkAddress)
    }
}

internal actual fun bind(
    selector: SelectorManager,
    localAddress: NetworkAddress?,
    socketOptions: SocketOptions.AcceptorOptions
): ServerSocket = selector.buildOrClose({ openServerSocketChannel() }) {
    assignOptions(socketOptions)
    nonBlocking()

    ServerSocketImpl(this, selector).apply {
        channel.socket().bind(localAddress, socketOptions.backlogSize)
    }
}
