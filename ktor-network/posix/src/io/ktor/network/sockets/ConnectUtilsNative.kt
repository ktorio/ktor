/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.sockets

import io.ktor.network.selector.*
import io.ktor.network.util.*
import io.ktor.utils.io.errors.*
import kotlinx.cinterop.*
import kotlinx.io.IOException
import platform.posix.*

private const val DEFAULT_BACKLOG_SIZE = 50

@OptIn(ExperimentalForeignApi::class)
internal actual suspend fun tcpConnect(
    selector: SelectorManager,
    remoteAddress: SocketAddress,
    socketOptions: SocketOptions.TCPClientSocketOptions
): Socket {
    initSocketsIfNeeded()

    var lastException: PosixException? = null
    for (remote in remoteAddress.resolve()) {
        try {
            val descriptor: Int = ktor_socket(remote.family.convert(), SOCK_STREAM, 0).check()

            val socket = buildOrCloseSocket(descriptor) {
                assignOptions(descriptor, socketOptions)
                nonBlocking(descriptor)

                TCPSocketNative(
                    selector,
                    descriptor,
                    remoteAddress = remote.toSocketAddress()
                )
            }

            try {
                return socket.connect(remote)
            } catch (throwable: Throwable) {
                socket.close()
                throw throwable
            }
        } catch (exception: PosixException) {
            lastException = exception
        }
    }

    throw IOException("Failed to connect to $remoteAddress.", lastException)
}

@OptIn(ExperimentalForeignApi::class)
internal actual suspend fun tcpBind(
    selector: SelectorManager,
    localAddress: SocketAddress?,
    socketOptions: SocketOptions.AcceptorOptions
): ServerSocket {
    initSocketsIfNeeded()

    val address = localAddress?.address ?: getAnyLocalAddress()
    val descriptor = ktor_socket(address.family.convert(), SOCK_STREAM, 0).check()

    buildOrCloseSocket(descriptor) {
        assignOptions(descriptor, socketOptions)
        nonBlocking(descriptor)

        address.nativeAddress { pointer, size ->
            ktor_bind(descriptor, pointer, size).check()
        }

        ktor_listen(descriptor, DEFAULT_BACKLOG_SIZE).check()

        val resolvedLocalAddress = getLocalAddress(descriptor)

        return TCPServerSocketNative(
            descriptor,
            selector,
            localAddress = resolvedLocalAddress.toSocketAddress(),
            parent = selector.coroutineContext
        )
    }
}
