/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.sockets

import io.ktor.network.selector.*
import java.nio.channels.*

internal class SocketImpl<out S : SocketChannel>(
    override val channel: S,
    selector: SelectorManager,
    socketOptions: SocketOptions.TCPClientSocketOptions? = null
) : NIOSocketImpl<S>(channel, selector, pool = null, socketOptions = socketOptions),
    Socket {
    init {
        require(!channel.isBlocking) { "Channel need to be configured as non-blocking." }
    }

    override val localAddress: SocketAddress
        get() = channel.localAddress?.toSocketAddress()
            ?: throw IllegalStateException("Channel is not yet bound")

    override val remoteAddress: SocketAddress
        get() = channel.remoteAddress?.toSocketAddress()
            ?: throw IllegalStateException("Channel is not yet connected")

    @Suppress("BlockingMethodInNonBlockingContext")
    internal suspend fun connect(target: java.net.SocketAddress): Socket {
        if (channel.connect(target)) return this

        wantConnect(true)
        selector.select(this, SelectInterest.CONNECT)

        while (true) {
            if (channel.finishConnect()) {
                // TCP has a well known self-connect problem, which client can connect to the client itself
                // without any program listen on the port.
                if (selfConnect()) {
                    channel.close()
                    continue
                }
                break
            }

            wantConnect(true)
            selector.select(this, SelectInterest.CONNECT)
        }

        wantConnect(false)

        return this
    }

    private fun wantConnect(state: Boolean = true) {
        interestOp(SelectInterest.CONNECT, state)
    }

    private fun selfConnect(): Boolean {
        val localAddress = channel.localAddress
        val remoteAddress = channel.remoteAddress

        if (localAddress == null || remoteAddress == null) {
            throw IllegalStateException("localAddress and remoteAddress should not be null.")
        }

        val localInetSocketAddress = localAddress as? java.net.InetSocketAddress
        val remoteInetSocketAddress = remoteAddress as? java.net.InetSocketAddress

        val localHostAddress = localInetSocketAddress?.address?.hostAddress ?: ""
        val remoteHostAddress = remoteInetSocketAddress?.address?.hostAddress ?: ""
        val isRemoteAnyLocalAddress = remoteInetSocketAddress?.address?.isAnyLocalAddress ?: false
        val localPort = localInetSocketAddress?.port
        val remotePort = remoteInetSocketAddress?.port

        return localPort == remotePort && (isRemoteAnyLocalAddress || localHostAddress == remoteHostAddress)
    }
}
