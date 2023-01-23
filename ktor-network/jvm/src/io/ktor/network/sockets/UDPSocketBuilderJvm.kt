/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.sockets

import io.ktor.network.selector.*
import io.ktor.util.*
import java.nio.channels.*

internal actual fun UDPSocketBuilder.Companion.connectUDP(
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

@OptIn(InternalAPI::class)
internal actual fun UDPSocketBuilder.Companion.bindUDP(
    selector: SelectorManager,
    localAddress: SocketAddress?,
    options: SocketOptions.UDPSocketOptions
): BoundDatagramSocket = bindUDPConfigurable(selector, localAddress, options)

@InternalAPI
public fun bindUDPConfigurable(
    selector: SelectorManager,
    localAddress: SocketAddress?,
    options: SocketOptions.UDPSocketOptions,
    configureDatagramChannel: DatagramChannel.() -> Unit = {}
): BoundDatagramSocket = selector.buildOrClose({ openDatagramChannel() }) {
    assignOptions(options)
    nonBlocking()
    configureDatagramChannel()

    if (java7NetworkApisAvailable) {
        bind(localAddress?.toJavaAddress())
    } else {
        socket().bind(localAddress?.toJavaAddress())
    }
    return DatagramSocketImpl(this, selector)
}
