/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.sockets

import io.ktor.network.dispatcher.*
import io.ktor.network.selector.*
import java.net.*
import java.nio.channels.*
import java.nio.channels.spi.*

internal class JvmTcpSocketBuilder(private val dispatcher: JvmSocketDispatcher) : TcpSocketBuilder {
    override suspend fun connect(
        remoteAddress: SocketAddress,
        configure: SocketOptions.TCPClientSocketOptions.() -> Unit
    ): Socket {
        val socketOptions = SocketOptions.create().peer().tcp().apply(configure) // FIXME
        return dispatcher.selector.buildOrClose({ openSocketChannelFor(remoteAddress) }) {
            if (remoteAddress is InetSocketAddress) assignOptions(socketOptions)
            nonBlocking()

            SocketImpl(this, dispatcher.selector, socketOptions).apply {
                connect(remoteAddress.toJavaAddress())
            }
        }
    }

    override suspend fun bind(
        localAddress: SocketAddress?,
        configure: SocketOptions.AcceptorOptions.() -> Unit
    ): ServerSocket {
        val socketOptions = SocketOptions.create().acceptor().apply(configure) // FIXME
        return dispatcher.selector.buildOrClose({ openServerSocketChannelFor(localAddress) }) {
            if (localAddress is InetSocketAddress) assignOptions(socketOptions)
            nonBlocking()

            ServerSocketImpl(this, dispatcher.selector).apply {
                if (java7NetworkApisAvailable) {
                    channel.bind(localAddress?.toJavaAddress(), socketOptions.backlogSize)
                } else {
                    channel.socket().bind(localAddress?.toJavaAddress(), socketOptions.backlogSize)
                }
            }
        }
    }
}


internal fun SelectorProvider.openSocketChannelFor(address: SocketAddress) = when (address) {
    is InetSocketAddress -> openSocketChannel()
    is UnixSocketAddress -> {
        val selectorProviderClass = SelectorProvider::class.java
        val protocolFamilyClass = ProtocolFamily::class.java
        val unixProtocolFamily = StandardProtocolFamily.valueOf("UNIX")
        val openSocketChannelMethod = selectorProviderClass.getMethod("openSocketChannel", protocolFamilyClass)
        openSocketChannelMethod.invoke(this, unixProtocolFamily) as SocketChannel
    }
}

internal fun SelectorProvider.openServerSocketChannelFor(address: SocketAddress?) = when (address) {
    null -> openServerSocketChannel()
    is InetSocketAddress -> openServerSocketChannel()
    is UnixSocketAddress -> {
        val selectorProviderClass = SelectorProvider::class.java
        val protocolFamilyClass = ProtocolFamily::class.java
        val unixProtocolFamily = StandardProtocolFamily.valueOf("UNIX")
        val openSocketChannelMethod = selectorProviderClass.getMethod("openServerSocketChannel", protocolFamilyClass)
        openSocketChannelMethod.invoke(this, unixProtocolFamily) as ServerSocketChannel
    }
}
