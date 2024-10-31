/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.sockets

import io.ktor.network.selector.*

internal actual suspend fun udpConnect(
    selector: SelectorManager,
    remoteAddress: SocketAddress,
    localAddress: SocketAddress?,
    options: SocketOptions.UDPSocketOptions
): ConnectedDatagramSocket = selector.buildOrClose({ openDatagramChannel() }) {
    assignOptions(options)
    nonBlocking()

    if (java7NetworkApisAvailable) {
        bind(localAddress?.toJavaAddress())
    } else {
        socket().bind(localAddress?.toJavaAddress())
    }
    connect(remoteAddress.toJavaAddress())

    return DatagramSocketImpl(this, selector)
}

internal actual suspend fun udpBind(
    selector: SelectorManager,
    localAddress: SocketAddress?,
    options: SocketOptions.UDPSocketOptions
): BoundDatagramSocket = selector.buildOrClose({ openDatagramChannel() }) {
    assignOptions(options)
    nonBlocking()

    if (java7NetworkApisAvailable) {
        bind(localAddress?.toJavaAddress())
    } else {
        socket().bind(localAddress?.toJavaAddress())
    }
    return DatagramSocketImpl(this, selector)
}
