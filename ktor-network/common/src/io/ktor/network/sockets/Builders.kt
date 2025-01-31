/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.sockets

import io.ktor.network.selector.*

/**
 * Start building a socket
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.network.sockets.aSocket)
 */
public fun aSocket(selector: SelectorManager): SocketBuilder = SocketBuilder(selector, SocketOptions.create())

/**
 * Socket builder
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.network.sockets.SocketBuilder)
 */
public class SocketBuilder internal constructor(
    private val selector: SelectorManager,
    override var options: SocketOptions
) : Configurable<SocketBuilder, SocketOptions> {

    /**
     * Build TCP socket.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.network.sockets.SocketBuilder.tcp)
     */
    public fun tcp(): TcpSocketBuilder = TcpSocketBuilder(selector, options.peer())

    /**
     * Build UDP socket.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.network.sockets.SocketBuilder.udp)
     */
    public fun udp(): UDPSocketBuilder = UDPSocketBuilder(selector, options.peer().udp())
}

/**
 * Set TCP_NODELAY socket option to disable the Nagle algorithm.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.network.sockets.tcpNoDelay)
 */
@Deprecated("noDelay is true by default", ReplaceWith("this"))
public fun <T : Configurable<T, *>> T.tcpNoDelay(): T {
    return configure {
        if (this is SocketOptions.TCPClientSocketOptions) {
            noDelay = true
        }
    }
}

/**
 * Represent a configurable socket
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.network.sockets.Configurable)
 */
public interface Configurable<out T : Configurable<T, Options>, Options : SocketOptions> {
    /**
     * Current socket options
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.network.sockets.Configurable.options)
     */
    public var options: Options

    /**
     * Configure socket options in [block] function
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.network.sockets.Configurable.configure)
     */
    public fun configure(block: Options.() -> Unit): T {
        @Suppress("UNCHECKED_CAST")
        val newOptions = options.copy() as Options

        block(newOptions)
        options = newOptions

        @Suppress("UNCHECKED_CAST")
        return this as T
    }
}
