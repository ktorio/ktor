/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.sockets

import java.net.*
import java.nio.channels.*

internal fun SelectableChannel.nonBlocking() {
    configureBlocking(false)
}

internal fun SelectableChannel.assignOptions(options: SocketOptions) {
    if (this is SocketChannel) {
        if (options.typeOfService != TypeOfService.UNDEFINED) {
            setOption(StandardSocketOptions.IP_TOS, options.typeOfService.intValue)
        }

        if (options.reuseAddress) {
            setOption(StandardSocketOptions.SO_REUSEADDR, true)
        }
        if (options.reusePort) {
            setOption(StandardSocketOptions.SO_REUSEPORT, true)
        }

        if (options is SocketOptions.PeerSocketOptions) {
            options.receiveBufferSize.takeIf { it > 0 }?.let { setOption(StandardSocketOptions.SO_RCVBUF, it) }
            options.sendBufferSize.takeIf { it > 0 }?.let { setOption(StandardSocketOptions.SO_SNDBUF, it) }
        }
        if (options is SocketOptions.TCPClientSocketOptions) {
            options.lingerSeconds.takeIf { it >= 0 }?.let { setOption(StandardSocketOptions.SO_LINGER, it) }
            options.keepAlive?.let { setOption(StandardSocketOptions.SO_KEEPALIVE, it) }
            setOption(StandardSocketOptions.TCP_NODELAY, options.noDelay)
        }
    }
    if (this is ServerSocketChannel) {
        if (options.reuseAddress) {
            setOption(StandardSocketOptions.SO_REUSEADDR, true)
        }
        if (options.reusePort) {
            setOption(StandardSocketOptions.SO_REUSEPORT, true)
        }
    }
    if (this is DatagramChannel) {
        if (options.typeOfService != TypeOfService.UNDEFINED) {
            setOption(StandardSocketOptions.IP_TOS, options.typeOfService.intValue)
        }

        if (options.reuseAddress) {
            setOption(StandardSocketOptions.SO_REUSEADDR, true)
        }
        if (options.reusePort) {
            setOption(StandardSocketOptions.SO_REUSEPORT, true)
        }

        if (options is SocketOptions.UDPSocketOptions) {
            setOption(StandardSocketOptions.SO_BROADCAST, options.broadcast)
        }
        if (options is SocketOptions.PeerSocketOptions) {
            options.receiveBufferSize.takeIf { it > 0 }?.let { setOption(StandardSocketOptions.SO_RCVBUF, it) }
            options.sendBufferSize.takeIf { it > 0 }?.let { setOption(StandardSocketOptions.SO_SNDBUF, it) }
        }
    }
}
