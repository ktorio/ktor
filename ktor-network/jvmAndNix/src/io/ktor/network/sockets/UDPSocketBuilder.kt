package io.ktor.network.sockets

import io.ktor.network.selector.*
import io.ktor.util.date.*

/**
 * UDP socket builder
 */
public class UDPSocketBuilder(
    private val selector: SelectorManager,
    override var options: SocketOptions.UDPSocketOptions,
    private val clock: GMTClock
) : Configurable<UDPSocketBuilder, SocketOptions.UDPSocketOptions> {
    /**
     * Bind server socket to listen to [localAddress].
     */
    public fun bind(
        localAddress: SocketAddress? = null,
        configure: SocketOptions.UDPSocketOptions.() -> Unit = {}
    ): BoundDatagramSocket = bindUDP(selector, localAddress, options.udp().apply(configure), clock)

    /**
     * Create a datagram socket to listen datagrams at [localAddress] and set to [remoteAddress].
     */
    public fun connect(
        remoteAddress: SocketAddress,
        localAddress: SocketAddress? = null,
        configure: SocketOptions.UDPSocketOptions.() -> Unit = {}
    ): ConnectedDatagramSocket =
        connectUDP(selector, remoteAddress, localAddress, options.udp().apply(configure), clock)

    public companion object
}

internal expect fun UDPSocketBuilder.Companion.connectUDP(
    selector: SelectorManager,
    remoteAddress: SocketAddress,
    localAddress: SocketAddress?,
    options: SocketOptions.UDPSocketOptions,
    clock: GMTClock
): ConnectedDatagramSocket

internal expect fun UDPSocketBuilder.Companion.bindUDP(
    selector: SelectorManager,
    localAddress: SocketAddress?,
    options: SocketOptions.UDPSocketOptions,
    clock: GMTClock
): BoundDatagramSocket
