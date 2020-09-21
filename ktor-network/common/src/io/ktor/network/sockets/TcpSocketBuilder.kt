package io.ktor.network.sockets

import io.ktor.network.selector.*
import io.ktor.util.network.*

/**
 * TCP socket builder
 */
@Suppress("PublicApiImplicitType")
public class TcpSocketBuilder(
    private val selector: SelectorManager,
    override var options: SocketOptions
) : Configurable<TcpSocketBuilder, SocketOptions> {
    /**
     * Connect to [hostname] and [port].
     */
    public suspend fun connect(
        hostname: String,
        port: Int,
        configure: SocketOptions.TCPClientSocketOptions.() -> Unit = {}
    ): Socket = connect(NetworkAddress(hostname, port), configure)

    /**
     * Bind server socket at [port] to listen to [hostname].
     */
    public fun bind(
        hostname: String = "0.0.0.0",
        port: Int = 0,
        configure: SocketOptions.AcceptorOptions.() -> Unit = {}
    ): ServerSocket = bind(NetworkAddress(hostname, port), configure)

    /**
     * Connect to [remoteAddress].
     */
    public suspend fun connect(
        remoteAddress: NetworkAddress,
        configure: SocketOptions.TCPClientSocketOptions.() -> Unit = {}
    ): Socket = connect(selector, remoteAddress, options.peer().tcp().apply(configure))

    /**
     * Bind server socket to listen to [localAddress].
     */
    public fun bind(
        localAddress: NetworkAddress? = null,
        configure: SocketOptions.AcceptorOptions.() -> Unit = {}
    ): ServerSocket = bind(selector, localAddress, options.peer().acceptor().apply(configure))
}
