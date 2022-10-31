/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.sockets

import io.ktor.network.dispatcher.*
import io.ktor.network.util.*
import kotlinx.cinterop.*
import platform.posix.*

private const val DEFAULT_BACKLOG_SIZE = 50

internal class NixTcpSocketBuilder(private val dispatcher: NixSocketDispatcher) : TcpSocketBuilder {
    override suspend fun connect(
        remoteAddress: SocketAddress,
        configure: SocketOptions.TCPClientSocketOptions.() -> Unit
    ): Socket = memScoped {
        val socketOptions = SocketOptions.create().peer().tcp().apply(configure) // FIXME

        for (remote in remoteAddress.resolve()) {
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
                    dispatcher.selector,
                    remoteAddress = remote.toSocketAddress(),
                    localAddress = localAddress.toSocketAddress()
                )
            } catch (_: Throwable) {
            }
        }

        error("Failed to connect to $remoteAddress.")
    }

    override suspend fun bind(
        localAddress: SocketAddress?,
        configure: SocketOptions.AcceptorOptions.() -> Unit
    ): ServerSocket = memScoped {
        val socketOptions = SocketOptions.create().acceptor().apply(configure) // FIXME
        val address = localAddress?.address ?: getAnyLocalAddress()
        val descriptor = socket(address.family.convert(), SOCK_STREAM, 0).check()

        assignOptions(descriptor, socketOptions)
        nonBlocking(descriptor)

        address.nativeAddress { pointer, size ->
            bind(descriptor, pointer, size).check()
        }

        listen(descriptor, DEFAULT_BACKLOG_SIZE).check()

        val resolvedLocalAddress = getLocalAddress(descriptor)

        return TCPServerSocketNative(
            descriptor,
            dispatcher.selector,
            localAddress = resolvedLocalAddress.toSocketAddress(),
            parent = dispatcher.selector.coroutineContext
        )
    }
}
