/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.sockets

import io.ktor.network.selector.*

/**
 * UDP socket builder
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.network.sockets.UDPSocketBuilder)
 */
public class UDPSocketBuilder internal constructor(
    private val selector: SelectorManager,
    override var options: SocketOptions.UDPSocketOptions
) : Configurable<UDPSocketBuilder, SocketOptions.UDPSocketOptions> {
    /**
     * Bind server socket to listen to [localAddress].
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.network.sockets.UDPSocketBuilder.bind)
     */
    public suspend fun bind(
        localAddress: SocketAddress? = null,
        configure: SocketOptions.UDPSocketOptions.() -> Unit = {}
    ): BoundDatagramSocket = udpBind(selector, localAddress, options.udp().apply(configure))

    /**
     * Bind server socket at [port] to listen to [hostname].
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.network.sockets.UDPSocketBuilder.bind)
     */
    public suspend fun bind(
        hostname: String = "0.0.0.0",
        port: Int = 0,
        configure: SocketOptions.UDPSocketOptions.() -> Unit = {}
    ): BoundDatagramSocket = bind(InetSocketAddress(hostname, port), configure)

    /**
     * Create a datagram socket to listen datagrams at [localAddress] and set to [remoteAddress].
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.network.sockets.UDPSocketBuilder.connect)
     */
    public suspend fun connect(
        remoteAddress: SocketAddress,
        localAddress: SocketAddress? = null,
        configure: SocketOptions.UDPSocketOptions.() -> Unit = {}
    ): ConnectedDatagramSocket = udpConnect(selector, remoteAddress, localAddress, options.udp().apply(configure))
}
