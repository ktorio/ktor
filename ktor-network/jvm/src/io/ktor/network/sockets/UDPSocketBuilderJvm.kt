/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.sockets

import io.ktor.network.selector.*
import io.ktor.util.network.*

internal actual fun UDPSocketBuilder.Companion.connectUDP(
    selector: SelectorManager,
    remoteAddress: NetworkAddress,
    localAddress: NetworkAddress?,
    options: SocketOptions.UDPSocketOptions
): ConnectedDatagramSocket = selector.buildOrClose({ openDatagramChannel() }) {
    assignOptions(options)
    nonBlocking()

    socket().bind(localAddress)
    connect(remoteAddress)

    return DatagramSocketImpl(this, selector)
}

internal actual fun UDPSocketBuilder.Companion.bindUDP(
    selector: SelectorManager,
    localAddress: NetworkAddress?,
    options: SocketOptions.UDPSocketOptions
): BoundDatagramSocket = selector.buildOrClose({ openDatagramChannel() }) {
    assignOptions(options)
    nonBlocking()

    socket().bind(localAddress)
    return DatagramSocketImpl(this, selector)
}
