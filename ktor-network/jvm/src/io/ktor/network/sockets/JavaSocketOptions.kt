/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.sockets

import java.net.*
import java.nio.channels.*

/**
 * Java 1.7 networking APIs (like [java.net.StandardSocketOptions]) are only available since Android API 24.
 */
internal val java7NetworkApisAvailable = try {
    Class.forName("java.net.StandardSocketOptions")
    true
} catch (exception: ClassNotFoundException) {
    false
}

internal fun SelectableChannel.nonBlocking() {
    configureBlocking(false)
}

internal fun SelectableChannel.assignOptions(options: SocketOptions) {
    if (this is SocketChannel) {
        if (options.typeOfService != TypeOfService.UNDEFINED) {
            if (java7NetworkApisAvailable) {
                setOption(StandardSocketOptions.IP_TOS, options.typeOfService.intValue)
            } else {
                socket().trafficClass = options.typeOfService.intValue
            }
        }

        if (options.reuseAddress) {
            if (java7NetworkApisAvailable) {
                setOption(StandardSocketOptions.SO_REUSEADDR, true)
            } else {
                socket().reuseAddress = true
            }
        }
        if (options.reusePort) {
            SocketOptionsPlatformCapabilities.setReusePort(this)
        }

        if (options is SocketOptions.PeerSocketOptions) {
            options.receiveBufferSize.takeIf { it > 0 }?.let {
                if (java7NetworkApisAvailable) {
                    setOption(StandardSocketOptions.SO_RCVBUF, it)
                } else {
                    socket().receiveBufferSize = it
                }
            }
            options.sendBufferSize.takeIf { it > 0 }?.let {
                if (java7NetworkApisAvailable) {
                    setOption(StandardSocketOptions.SO_SNDBUF, it)
                } else {
                    socket().sendBufferSize = it
                }
            }
        }
        if (options is SocketOptions.TCPClientSocketOptions) {
            options.lingerSeconds.takeIf { it >= 0 }?.let {
                if (java7NetworkApisAvailable) {
                    setOption(StandardSocketOptions.SO_LINGER, it)
                } else {
                    socket().setSoLinger(true, it)
                }
            }
            options.keepAlive?.let {
                if (java7NetworkApisAvailable) {
                    setOption(StandardSocketOptions.SO_KEEPALIVE, it)
                } else {
                    socket().keepAlive = it
                }
            }
            if (java7NetworkApisAvailable) {
                setOption(StandardSocketOptions.TCP_NODELAY, options.noDelay)
            } else {
                socket().tcpNoDelay = options.noDelay
            }
        }
    }
    if (this is ServerSocketChannel) {
        if (options.reuseAddress) {
            if (java7NetworkApisAvailable) {
                setOption(StandardSocketOptions.SO_REUSEADDR, true)
            } else {
                socket().reuseAddress = true
            }
        }
        if (options.reusePort) {
            SocketOptionsPlatformCapabilities.setReusePort(this)
        }
    }
    if (this is DatagramChannel) {
        if (options.typeOfService != TypeOfService.UNDEFINED) {
            if (java7NetworkApisAvailable) {
                setOption(StandardSocketOptions.IP_TOS, options.typeOfService.intValue)
            } else {
                socket().trafficClass = options.typeOfService.intValue
            }
        }

        if (options.reuseAddress) {
            if (java7NetworkApisAvailable) {
                setOption(StandardSocketOptions.SO_REUSEADDR, true)
            } else {
                socket().reuseAddress = true
            }
        }
        if (options.reusePort) {
            SocketOptionsPlatformCapabilities.setReusePort(this)
        }

        if (options is SocketOptions.UDPSocketOptions) {
            if (java7NetworkApisAvailable) {
                setOption(StandardSocketOptions.SO_BROADCAST, options.broadcast)
            } else {
                socket().broadcast = options.broadcast
            }
        }
        if (options is SocketOptions.PeerSocketOptions) {
            options.receiveBufferSize.takeIf { it > 0 }?.let {
                if (java7NetworkApisAvailable) {
                    setOption(StandardSocketOptions.SO_RCVBUF, it)
                } else {
                    socket().receiveBufferSize = it
                }
            }
            options.sendBufferSize.takeIf { it > 0 }?.let {
                if (java7NetworkApisAvailable) {
                    setOption(StandardSocketOptions.SO_SNDBUF, it)
                } else {
                    socket().sendBufferSize = it
                }
            }
        }
    }
}
