/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.sockets

/**
 * UDP socket builder
 */
public interface UdpSocketBuilder {
    /**
     * Bind server socket to listen to [localAddress].
     */
    public suspend fun bind(
        localAddress: SocketAddress? = null,
        configure: SocketOptions.UDPSocketOptions.() -> Unit = {}
    ): BoundDatagramSocket

    /**
     * Create a datagram socket to listen datagrams at [localAddress] and set to [remoteAddress].
     */
    public suspend fun connect(
        remoteAddress: SocketAddress,
        localAddress: SocketAddress? = null,
        configure: SocketOptions.UDPSocketOptions.() -> Unit = {}
    ): ConnectedDatagramSocket
}
