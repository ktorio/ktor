/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.sockets

import io.ktor.network.selector.*

/**
 * TCP socket builder
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.network.sockets.TcpSocketBuilder)
 */
public class TcpSocketBuilder internal constructor(
    private val selector: SelectorManager,
    override var options: SocketOptions.PeerSocketOptions
) : Configurable<TcpSocketBuilder, SocketOptions.PeerSocketOptions> {
    /**
     * Connect to [hostname] and [port].
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.network.sockets.TcpSocketBuilder.connect)
     */
    public suspend fun connect(
        hostname: String,
        port: Int,
        configure: SocketOptions.TCPClientSocketOptions.() -> Unit = {}
    ): Socket = connect(InetSocketAddress(hostname, port), configure)

    /**
     * Bind server socket at [port] to listen to [hostname].
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.network.sockets.TcpSocketBuilder.bind)
     */
    public suspend fun bind(
        hostname: String = "0.0.0.0",
        port: Int = 0,
        configure: SocketOptions.AcceptorOptions.() -> Unit = {}
    ): ServerSocket = bind(InetSocketAddress(hostname, port), configure)

    /**
     * Connect to [remoteAddress].
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.network.sockets.TcpSocketBuilder.connect)
     */
    public suspend fun connect(
        remoteAddress: SocketAddress,
        configure: SocketOptions.TCPClientSocketOptions.() -> Unit = {}
    ): Socket = tcpConnect(selector, remoteAddress, options.tcpConnect().apply(configure))

    /**
     * Bind server socket to listen to [localAddress].
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.network.sockets.TcpSocketBuilder.bind)
     */
    public suspend fun bind(
        localAddress: SocketAddress? = null,
        configure: SocketOptions.AcceptorOptions.() -> Unit = {}
    ): ServerSocket = tcpBind(selector, localAddress, options.tcpAccept().apply(configure))
}
