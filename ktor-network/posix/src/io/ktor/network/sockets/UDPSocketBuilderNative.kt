/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.sockets

import io.ktor.network.selector.*
import io.ktor.network.util.*
import io.ktor.util.network.*
import kotlinx.cinterop.*
import platform.posix.*

internal actual fun UDPSocketBuilder.Companion.connectUDP(
    selector: SelectorManager,
    remoteAddress: NetworkAddress,
    localAddress: NetworkAddress?,
    options: SocketOptions.UDPSocketOptions
): ConnectedDatagramSocket {
    val address = localAddress?.address ?: getAnyLocalAddress()
    val descriptor = socket(address.family.convert(), SOCK_DGRAM, 0).check()

    assignOptions(descriptor, options)
    nonBlocking(descriptor)

    address.nativeAddress { address, size ->
        bind(descriptor, address, size).check()
    }

    remoteAddress.address.nativeAddress { address, size ->
        connect(descriptor, address, size).check()
    }

    return DatagramSocketImpl(
        descriptor,
        selector,
        _localAddress = localAddress ?: NetworkAddress("0.0.0.0", address.port, address),
        _remoteAddress = remoteAddress,
        parent = selector.coroutineContext
    )
}

internal actual fun UDPSocketBuilder.Companion.bindUDP(
    selector: SelectorManager,
    localAddress: NetworkAddress?,
    options: SocketOptions.UDPSocketOptions
): BoundDatagramSocket {
    val address = localAddress?.address ?: getAnyLocalAddress()
    val descriptor = socket(address.family.convert(), SOCK_DGRAM, 0).check()

    assignOptions(descriptor, options)
    nonBlocking(descriptor)

    address.nativeAddress { address, size ->
        bind(descriptor, address, size).check()
    }

    return DatagramSocketImpl(
        descriptor,
        selector,
        _localAddress = localAddress ?: NetworkAddress("0.0.0.0", address.port, address),
        _remoteAddress = null,
        parent = selector.coroutineContext
    )
}
