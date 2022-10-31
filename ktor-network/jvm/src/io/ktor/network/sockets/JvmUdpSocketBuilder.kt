/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.sockets

import io.ktor.network.dispatcher.*
import io.ktor.network.selector.*

internal class JvmUdpSocketBuilder(private val dispatcher: JvmSocketDispatcher) : UdpSocketBuilder {
    override suspend fun bind(
        localAddress: SocketAddress?,
        configure: SocketOptions.UDPSocketOptions.() -> Unit
    ): BoundDatagramSocket {
        val options = SocketOptions.create().peer().udp().apply(configure) // FIXME
        return dispatcher.selector.buildOrClose({ openDatagramChannel() }) {
            assignOptions(options)
            nonBlocking()

            if (java7NetworkApisAvailable) {
                bind(localAddress?.toJavaAddress())
            } else {
                socket().bind(localAddress?.toJavaAddress())
            }
            return DatagramSocketImpl(this, dispatcher.selector)
        }
    }

    override suspend fun connect(
        remoteAddress: SocketAddress,
        localAddress: SocketAddress?,
        configure: SocketOptions.UDPSocketOptions.() -> Unit
    ): ConnectedDatagramSocket {
        val options = SocketOptions.create().peer().udp().apply(configure)
        return dispatcher.selector.buildOrClose({ openDatagramChannel() }) {
            assignOptions(options)
            nonBlocking()

            if (java7NetworkApisAvailable) {
                bind(localAddress?.toJavaAddress())
            } else {
                socket().bind(localAddress?.toJavaAddress())
            }
            connect(remoteAddress.toJavaAddress())

            return DatagramSocketImpl(this, dispatcher.selector)
        }
    }
}
