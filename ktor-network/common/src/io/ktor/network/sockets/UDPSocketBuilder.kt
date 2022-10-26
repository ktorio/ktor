/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.sockets

import io.ktor.network.selector.*

/**
 * UDP socket builder
 */
public class UDPSocketBuilder(
    private val selector: SelectorManager,
    override var options: SocketOptions.UDPSocketOptions
) : Configurable<UDPSocketBuilder, SocketOptions.UDPSocketOptions> {
    /**
     * Bind server socket to listen to [localAddress].
     */
    public fun bind(
        localAddress: SocketAddress? = null,
        configure: SocketOptions.UDPSocketOptions.() -> Unit = {}
    ): BoundDatagramSocket = bindUDP(selector, localAddress, options.udp().apply(configure))

    /**
     * Create a datagram socket to listen datagrams at [localAddress] and set to [remoteAddress].
     */
    public fun connect(
        remoteAddress: SocketAddress,
        localAddress: SocketAddress? = null,
        configure: SocketOptions.UDPSocketOptions.() -> Unit = {}
    ): ConnectedDatagramSocket = connectUDP(selector, remoteAddress, localAddress, options.udp().apply(configure))

    public companion object
}
