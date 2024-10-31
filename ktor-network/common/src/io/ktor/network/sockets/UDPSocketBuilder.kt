/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.sockets

import io.ktor.network.selector.*

/**
 * UDP socket builder
 */
public class UDPSocketBuilder internal constructor(
    private val selector: SelectorManager,
    override var options: SocketOptions.UDPSocketOptions
) : Configurable<UDPSocketBuilder, SocketOptions.UDPSocketOptions> {
    /**
     * Bind server socket to listen to [localAddress].
     */
    public suspend fun bind(
        localAddress: SocketAddress? = null,
        configure: SocketOptions.UDPSocketOptions.() -> Unit = {}
    ): BoundDatagramSocket = udpBind(selector, localAddress, options.udp().apply(configure))

    /**
     * Create a datagram socket to listen datagrams at [localAddress] and set to [remoteAddress].
     */
    public suspend fun connect(
        remoteAddress: SocketAddress,
        localAddress: SocketAddress? = null,
        configure: SocketOptions.UDPSocketOptions.() -> Unit = {}
    ): ConnectedDatagramSocket = udpConnect(selector, remoteAddress, localAddress, options.udp().apply(configure))
}
