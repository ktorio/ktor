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

            fcntl(descriptor, F_SETFL, O_NONBLOCK).check()

            val localAddress = getLocalAddress(descriptor)

            return TCPSocketNative(
                descriptor,
                selector,
                remoteAddress = NetworkAddress(
                    networkAddress.hostname,
                    networkAddress.port,
                    remote
                ),
                localAddress = NetworkAddress("0.0.0.0", localAddress.port, localAddress)
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

    fcntl(descriptor, F_SETFL, O_NONBLOCK).check { it == 0 }

    address.nativeAddress { address, size ->
        bind(descriptor, address, size).check()
    }

    listen(descriptor, DEFAULT_BACKLOG_SIZE).check()

    return TCPServerSocketNative(
        descriptor,
        selector,
        localAddress = localAddress ?: NetworkAddress("0.0.0.0", address.port, address),
        parent = selector.coroutineContext
    )
}
