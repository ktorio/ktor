/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.sockets

import io.ktor.network.selector.*

/**
 * TCP socket builder
 */
public class TcpSocketBuilder internal constructor(
    private val selector: SelectorManager,
    override var options: SocketOptions.PeerSocketOptions
) : Configurable<TcpSocketBuilder, SocketOptions.PeerSocketOptions> {
    /**
     * Connect to [hostname] and [port].
     */
    public suspend fun connect(
        hostname: String,
        port: Int,
        configure: SocketOptions.TCPClientSocketOptions.() -> Unit = {}
    ): Socket = connect(InetSocketAddress(hostname, port), configure)

    /**
     * Bind server socket at [port] to listen to [hostname].
     */
    public suspend fun bind(
        hostname: String = "0.0.0.0",
        port: Int = 0,
        configure: SocketOptions.AcceptorOptions.() -> Unit = {}
    ): ServerSocket = bind(InetSocketAddress(hostname, port), configure)

    /**
     * Connect to [remoteAddress].
     */
    public suspend fun connect(
        remoteAddress: SocketAddress,
        configure: SocketOptions.TCPClientSocketOptions.() -> Unit = {}
    ): Socket = tcpConnect(selector, remoteAddress, options.tcpConnect().apply(configure))

    /**
     * Bind server socket to listen to [localAddress].
     */
    public suspend fun bind(
        localAddress: SocketAddress? = null,
        configure: SocketOptions.AcceptorOptions.() -> Unit = {}
    ): ServerSocket = tcpBind(selector, localAddress, options.tcpAccept().apply(configure))
}
