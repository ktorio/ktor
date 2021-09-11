/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.network.sockets

import io.ktor.network.selector.*

internal actual suspend fun connect(
    selector: SelectorManager,
    remoteAddress: SocketAddress,
    socketOptions: SocketOptions.TCPClientSocketOptions
): Socket = selector.buildOrClose({ openSocketChannel() }) {
    assignOptions(socketOptions)
    nonBlocking()

    SocketImpl(this, socket()!!, selector, socketOptions).apply {
        connect(remoteAddress.toJavaAddress())
    }
}

internal actual fun bind(
    selector: SelectorManager,
    localAddress: SocketAddress?,
    socketOptions: SocketOptions.AcceptorOptions
): ServerSocket = selector.buildOrClose({ openServerSocketChannel() }) {
    assignOptions(socketOptions)
    nonBlocking()

    ServerSocketImpl(this, selector).apply {
        channel.socket().bind(localAddress?.toJavaAddress(), socketOptions.backlogSize)
    }
}
