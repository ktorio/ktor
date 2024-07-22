/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.network.sockets

import io.ktor.network.selector.*
import io.ktor.network.util.*
import io.ktor.utils.io.errors.*
import kotlinx.cinterop.*
import kotlinx.io.IOException
import platform.posix.*

private const val DEFAULT_BACKLOG_SIZE = 50

@OptIn(UnsafeNumber::class, ExperimentalForeignApi::class)
internal actual suspend fun connect(
    selector: SelectorManager,
    remoteAddress: SocketAddress,
    socketOptions: SocketOptions.TCPClientSocketOptions
): Socket = memScoped {
    var lastException: PosixException? = null
    for (remote in remoteAddress.resolve()) {
        try {
            val descriptor: Int = socket(remote.family.convert(), SOCK_STREAM, 0).check()
            val selectable = SelectableNative(descriptor)

            try {
                assignOptions(descriptor, socketOptions)
                nonBlocking(descriptor)

                var connectResult = -1
                remote.nativeAddress { address, size ->
                    connectResult = connect(descriptor, address, size)
                }

                if (connectResult < 0 && errno == EINPROGRESS) {
                    while (true) {
                        selector.select(selectable, SelectInterest.CONNECT)
                        val result = alloc<IntVar>()
                        val size = alloc<socklen_tVar> {
                            value = sizeOf<IntVar>().convert()
                        }
                        getsockopt(descriptor, SOL_SOCKET, SO_ERROR, result.ptr, size.ptr).check()
                        when (result.value) {
                            0 -> break // connected
                            EINPROGRESS -> continue
                            else -> throw PosixException.forErrno(errno = result.value)
                        }
                    }
                } else {
                    connectResult.check()
                }

                val localAddress = getLocalAddress(descriptor)

                return TCPSocketNative(
                    descriptor,
                    selector,
                    selectable,
                    remoteAddress = remote.toSocketAddress(),
                    localAddress = localAddress.toSocketAddress()
                )
            } catch (throwable: Throwable) {
                shutdown(descriptor, SHUT_RDWR)
                // Descriptor is closed by the selector manager
                selector.notifyClosed(selectable)
                throw throwable
            }
        } catch (exception: PosixException) {
            lastException = exception
        }
    }

    throw IOException("Failed to connect to $remoteAddress.", lastException)
}

@OptIn(UnsafeNumber::class, ExperimentalForeignApi::class)
internal actual fun bind(
    selector: SelectorManager,
    localAddress: SocketAddress?,
    socketOptions: SocketOptions.AcceptorOptions
): ServerSocket = memScoped {
    val address = localAddress?.address ?: getAnyLocalAddress()

    val descriptor = socket(address.family.convert(), SOCK_STREAM, 0).check()
    val selectable = SelectableNative(descriptor)

    try {
        assignOptions(descriptor, socketOptions)
        nonBlocking(descriptor)

        address.nativeAddress { pointer, size ->
            bind(descriptor, pointer, size).check()
        }

        listen(descriptor, DEFAULT_BACKLOG_SIZE).check()

        val resolvedLocalAddress = getLocalAddress(descriptor)

        return TCPServerSocketNative(
            descriptor,
            selector,
            selectable,
            localAddress = resolvedLocalAddress.toSocketAddress(),
            parent = selector.coroutineContext
        )
    } catch (throwable: Throwable) {
        shutdown(descriptor, SHUT_RDWR)
        // Descriptor is closed by the selector manager
        selector.notifyClosed(selectable)
        throw throwable
    }
}
