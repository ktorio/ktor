/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.sockets

import io.ktor.network.selector.*
import java.net.*
import java.nio.channels.*

/**
 * Represent a configurable socket
 */
interface Configurable<out T : Configurable<T, O>, O : SocketOptions> {
    /**
     * Current socket options
     */
    var options: O

    /**
     * Configure socket options in [block] function
     */
    fun configure(block: O.() -> Unit): T {
        @Suppress("UNCHECKED_CAST")
        val newOptions = options.copy() as O

        block(newOptions)
        options = newOptions

        @Suppress("UNCHECKED_CAST")
        return this as T
    }
}

/**
 * Set TCP_NODELAY socket option to disable the Nagle algorithm.
 */
fun <T : Configurable<T, *>> T.tcpNoDelay(): T {
    return configure {
        if (this is SocketOptions.TCPClientSocketOptions) {
            noDelay = true
        }
    }
}

/**
 * Start building a socket
 */
fun aSocket(selector: SelectorManager): SocketBuilder = SocketBuilder(selector, SocketOptions.create())

/**
 * Socket builder
 */
@Suppress("PublicApiImplicitType", "unused")
class SocketBuilder internal constructor(private val selector: SelectorManager, override var options: SocketOptions) :
    Configurable<SocketBuilder, SocketOptions> {
    /**
     * Build TCP socket
     */
    fun tcp() = TcpSocketBuilder(selector, options.peer())

    /**
     * Build UDP socket
     */
    fun udp() = UDPSocketBuilder(selector, options.peer().udp())
}

/**
 * TCP socket builder
 */
@Suppress("PublicApiImplicitType")
class TcpSocketBuilder internal constructor(
    private val selector: SelectorManager,
    override var options: SocketOptions
) : Configurable<TcpSocketBuilder, SocketOptions> {
    /**
     * Connect to [hostname] and [port]
     */
    suspend fun connect(
        hostname: String,
        port: Int,
        configure: SocketOptions.TCPClientSocketOptions.() -> Unit = {}
    ): Socket = connect(InetSocketAddress(hostname, port), configure)

    /**
     * Bind server socket at [port] to listen to [hostname]
     */
    fun bind(
        hostname: String = "0.0.0.0",
        port: Int = 0,
        configure: SocketOptions.AcceptorOptions.() -> Unit = {}
    ): ServerSocket = bind(InetSocketAddress(hostname, port), configure)

    /**
     * Connect to [remoteAddress]
     */
    suspend fun connect(
        remoteAddress: SocketAddress,
        configure: SocketOptions.TCPClientSocketOptions.() -> Unit = {}
    ): Socket = selector.buildOrClose({ openSocketChannel() }) {
        val options = options.peer().tcp()
        configure(options)
        assignOptions(options)
        nonBlocking()

        SocketImpl(this, socket()!!, selector, options).apply {
            connect(remoteAddress)
        }
    }

    /**
     * Bind server socket to listen to [localAddress]
     */
    fun bind(
        localAddress: SocketAddress? = null,
        configure: SocketOptions.AcceptorOptions.() -> Unit = {}
    ): ServerSocket {
        return selector.buildOrClose({ openServerSocketChannel() }) {
            val options = options.acceptor()
            configure(options)
            assignOptions(options)
            nonBlocking()

            ServerSocketImpl(this, selector).apply {
                channel.socket().bind(localAddress)
            }
        }
    }
}

/**
 * UDP socket builder
 */
class UDPSocketBuilder internal constructor(
    private val selector: SelectorManager,
    override var options: SocketOptions.UDPSocketOptions
) : Configurable<UDPSocketBuilder, SocketOptions.UDPSocketOptions> {
    /**
     * Bind server socket to listen to [localAddress]
     */
    fun bind(
        localAddress: SocketAddress? = null,
        configure: SocketOptions.UDPSocketOptions.() -> Unit = {}
    ): BoundDatagramSocket {
        return selector.buildOrClose({ openDatagramChannel() }) {
            val options = options.udp()
            configure(options)
            assignOptions(options)
            nonBlocking()

            DatagramSocketImpl(this, selector).apply {
                channel.socket().bind(localAddress)
            }
        }
    }

    /**
     * Create a datagram socket to listen datagrams at [localAddress] and set to [remoteAddress]
     */
    fun connect(
        remoteAddress: SocketAddress, localAddress: SocketAddress? = null,
        configure: SocketOptions.UDPSocketOptions.() -> Unit = {}
    ): ConnectedDatagramSocket {
        return selector.buildOrClose({ openDatagramChannel() }) {
            val options = options.udp()
            configure(options)
            assignOptions(options)
            nonBlocking()

            DatagramSocketImpl(this, selector).apply {
                channel.socket().bind(localAddress)
                channel.connect(remoteAddress)
            }
        }
    }
}

private fun SelectableChannel.nonBlocking() {
    configureBlocking(false)
}
