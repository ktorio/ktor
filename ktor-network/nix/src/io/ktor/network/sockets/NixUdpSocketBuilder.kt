/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.sockets

import io.ktor.network.dispatcher.*
import io.ktor.network.util.*
import kotlinx.cinterop.*
import platform.posix.*

internal class NixUdpSocketBuilder(private val dispatcher: NixSocketDispatcher) : UdpSocketBuilder {
    override suspend fun bind(
        localAddress: SocketAddress?,
        configure: SocketOptions.UDPSocketOptions.() -> Unit
    ): BoundDatagramSocket {
        val options = SocketOptions.create().peer().udp().apply(configure) // FIXME
        val address = localAddress?.address ?: getAnyLocalAddress()
        val descriptor = socket(address.family.convert(), SOCK_DGRAM, 0).check()

        assignOptions(descriptor, options)
        nonBlocking(descriptor)

        address.nativeAddress { pointer, size ->
            bind(descriptor, pointer, size).check()
        }

        return DatagramSocketNative(
            descriptor = descriptor,
            selector = dispatcher.selector,
            parent = dispatcher.selector.coroutineContext
        )
    }

    override suspend fun connect(
        remoteAddress: SocketAddress,
        localAddress: SocketAddress?,
        configure: SocketOptions.UDPSocketOptions.() -> Unit
    ): ConnectedDatagramSocket {
        val options = SocketOptions.create().peer().udp().apply(configure)
        val address = localAddress?.address ?: getAnyLocalAddress()
        val descriptor = socket(address.family.convert(), SOCK_DGRAM, 0).check()

        assignOptions(descriptor, options)
        nonBlocking(descriptor)

        address.nativeAddress { pointer, size ->
            bind(descriptor, pointer, size).check()
        }

        remoteAddress.address.nativeAddress { pointer, size ->
            connect(descriptor, pointer, size).check()
        }

        return DatagramSocketNative(
            descriptor = descriptor,
            selector = dispatcher.selector,
            parent = dispatcher.selector.coroutineContext
        )
    }
}
