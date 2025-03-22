/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.sockets

import io.ktor.network.selector.*
import io.ktor.network.util.*
import kotlinx.cinterop.*
import platform.posix.*

@OptIn(ExperimentalForeignApi::class)
internal actual suspend fun udpConnect(
    selector: SelectorManager,
    remoteAddress: SocketAddress,
    localAddress: SocketAddress?,
    options: SocketOptions.UDPSocketOptions
): ConnectedDatagramSocket {
    initSocketsIfNeeded()

    val address = localAddress?.address ?: getAnyLocalAddress()

    val descriptor = ktor_socket(address.family.convert(), SOCK_DGRAM, 0).check()

    buildOrCloseSocket(descriptor) {
        assignOptions(descriptor, options)
        nonBlocking(descriptor)

        address.nativeAddress { pointer, size ->
            ktor_bind(descriptor, pointer, size).check()
        }

        remoteAddress.address.nativeAddress { pointer, size ->
            ktor_connect(descriptor, pointer, size).check()
        }

        return DatagramSocketNative(
            selector = selector,
            descriptor = descriptor,
            remote = remoteAddress,
            parent = selector.coroutineContext
        )
    }
}

@OptIn(ExperimentalForeignApi::class)
internal actual suspend fun udpBind(
    selector: SelectorManager,
    localAddress: SocketAddress?,
    options: SocketOptions.UDPSocketOptions
): BoundDatagramSocket {
    initSocketsIfNeeded()

    val address = localAddress?.address ?: getAnyLocalAddress()

    val descriptor = ktor_socket(address.family.convert(), SOCK_DGRAM, 0).check()

    buildOrCloseSocket(descriptor) {
        assignOptions(descriptor, options)
        nonBlocking(descriptor)

        address.nativeAddress { pointer, size ->
            ktor_bind(descriptor, pointer, size).check()
        }

        return DatagramSocketNative(
            selector = selector,
            descriptor = descriptor,
            remote = null,
            parent = selector.coroutineContext
        )
    }
}
