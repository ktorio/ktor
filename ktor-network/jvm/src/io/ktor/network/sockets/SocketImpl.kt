/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.sockets

import io.ktor.network.selector.*
import io.ktor.util.network.*
import java.net.InetSocketAddress
import java.net.SocketAddress
import java.nio.channels.*

internal class SocketImpl<out S : SocketChannel>(
    override val channel: S,
    private val socket: java.net.Socket,
    selector: SelectorManager,
    socketOptions: SocketOptions.TCPClientSocketOptions? = null
) : NIOSocketImpl<S>(channel, selector, pool = null, socketOptions = socketOptions),
    Socket {
    init {
        require(!channel.isBlocking) { "Channel need to be configured as non-blocking." }
    }

    override val localAddress: NetworkAddress
        get() = socket.localSocketAddress

    override val remoteAddress: NetworkAddress
        get() = socket.remoteSocketAddress

    @Suppress("BlockingMethodInNonBlockingContext")
    internal suspend fun connect(target: SocketAddress): Socket {
        if (channel.connect(target)) return this

        wantConnect(true)
        selector.select(this, SelectInterest.CONNECT)

        while (true) {
            if (channel.finishConnect()) {
                // TCP has a well known self-connect problem, which client can connect to the client itself
                // without any program listen on the port.
                if (selfConnect()) {
                    this.socket.close()
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
        val localHostAddress = (this.localAddress as? InetSocketAddress)?.address?.hostAddress ?: ""
        val remoteHostAddress = (this.remoteAddress as? InetSocketAddress)?.address?.hostAddress ?: ""
        val isRemoteAnyLocalAddress = (this.remoteAddress as? InetSocketAddress)?.address?.isAnyLocalAddress ?: false
        val localPort = this.localAddress.port
        val remotePort = this.remoteAddress.port
        return localPort == remotePort && (isRemoteAnyLocalAddress || localHostAddress == remoteHostAddress)
    }
}
