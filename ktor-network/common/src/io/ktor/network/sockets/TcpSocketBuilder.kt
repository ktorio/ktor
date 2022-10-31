package io.ktor.network.sockets

/**
 * TCP socket builder
 */
public interface TcpSocketBuilder {
    /**
     * Connect to [remoteAddress].
     */
    public suspend fun connect(
        remoteAddress: SocketAddress,
        configure: SocketOptions.TCPClientSocketOptions.() -> Unit = {}
    ): Socket

    /**
     * Bind server socket to listen to [localAddress].
     */
    public suspend fun bind(
        localAddress: SocketAddress? = null,
        configure: SocketOptions.AcceptorOptions.() -> Unit = {}
    ): ServerSocket
}

/**
 * Connect to [hostname] and [port].
 */
public suspend inline fun TcpSocketBuilder.connect(
    hostname: String,
    port: Int,
    noinline configure: SocketOptions.TCPClientSocketOptions.() -> Unit = {}
): Socket = connect(InetSocketAddress(hostname, port), configure)

/**
 * Bind server socket at [port] to listen to [hostname].
 */
public suspend inline fun TcpSocketBuilder.bind(
    hostname: String = "0.0.0.0",
    port: Int = 0,
    noinline configure: SocketOptions.AcceptorOptions.() -> Unit = {}
): ServerSocket = bind(InetSocketAddress(hostname, port), configure)
