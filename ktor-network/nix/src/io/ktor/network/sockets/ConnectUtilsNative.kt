/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.network.sockets

import io.ktor.network.selector.*
import io.ktor.network.util.*
import io.ktor.util.network.*
import kotlinx.cinterop.*
import platform.posix.*

private const val DEFAULT_BACKLOG_SIZE = 50

internal actual suspend fun connect(
    selector: SelectorManager,
    networkAddress: NetworkAddress,
    socketOptions: SocketOptions.TCPClientSocketOptions
): Socket = memScoped {
    for (remote in networkAddress.resolve()) {
        try {
            val descriptor: Int = socket(remote.family.convert(), SOCK_STREAM, 0).check()

            remote.nativeAddress { address, size ->
                connect(descriptor, address, size).check()
            }

            assignOptions(descriptor, socketOptions)
            nonBlocking(descriptor)

            val localAddress = getLocalAddress(descriptor)

            return TCPSocketNative(
                descriptor,
                selector,
                remoteAddress = ResolvedNetworkAddress(
                    networkAddress.hostname,
                    networkAddress.port,
                    remote
                ),
                localAddress = ResolvedNetworkAddress("0.0.0.0", localAddress.port, localAddress)
            )
        } catch (_: Throwable) {
        }
    }

    error("Failed to connect to $networkAddress.")
}

internal actual fun bind(
    selector: SelectorManager,
    localAddress: NetworkAddress?,
    socketOptions: SocketOptions.AcceptorOptions
): ServerSocket = memScoped {
    val address = localAddress?.address ?: getAnyLocalAddress()
    val descriptor = socket(address.family.convert(), SOCK_STREAM, 0).check()

    assignOptions(descriptor, socketOptions)
    nonBlocking(descriptor)

    address.nativeAddress { pointer, size ->
        bind(descriptor, pointer, size).check()
    }

    listen(descriptor, DEFAULT_BACKLOG_SIZE).check()

    val localAddress = getLocalAddress(descriptor)

    return TCPServerSocketNative(
        descriptor,
        selector,
        localAddress = NetworkAddress(localAddress.address, localAddress.port),
        parent = selector.coroutineContext
    )
}
