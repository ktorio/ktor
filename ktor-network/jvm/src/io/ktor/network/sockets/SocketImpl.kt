/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.sockets

import io.ktor.network.selector.*
import java.net.*
import java.nio.channels.*

internal class SocketImpl<out S : SocketChannel>(
    override val channel: S,
    private val socket: java.net.Socket,
    selector: SelectorManager,
    socketOptions: SocketOptions.TCPClientSocketOptions? = null
) : NIOSocketImpl<S>(channel, selector, pool = null, socketOptions = socketOptions),
    Socket {
    init {
        require(!channel.isBlocking) { "channel need to be configured as non-blocking" }
    }

    override val localAddress: SocketAddress
        get() = socket.localSocketAddress

    override val remoteAddress: SocketAddress
        get() = socket.remoteSocketAddress

    @Suppress("BlockingMethodInNonBlockingContext")
    internal suspend fun connect(target: SocketAddress): Socket {
        if (channel.connect(target)) return this

        wantConnect(true)
        selector.select(this, SelectInterest.CONNECT)

        while (true) {
            if (channel.finishConnect()) break

            wantConnect(true)
            selector.select(this, SelectInterest.CONNECT)
        }

        wantConnect(false)

        return this
    }

    private fun wantConnect(state: Boolean = true) {
        interestOp(SelectInterest.CONNECT, state)
    }
}
