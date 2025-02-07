/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.sockets

import io.ktor.network.selector.*
import kotlinx.coroutines.*
import java.net.*
import java.nio.channels.*

@Suppress("BlockingMethodInNonBlockingContext")
internal class ServerSocketImpl(
    override val channel: ServerSocketChannel,
    val selector: SelectorManager
) : SelectableBase(), ServerSocket {
    init {
        require(!channel.isBlocking) { "Channel need to be configured as non-blocking." }
    }

    override val socketContext: CompletableJob = Job()

    override val localAddress: SocketAddress
        get() {
            val localAddress = if (java7NetworkApisAvailable) {
                channel.localAddress
            } else {
                channel.socket().localSocketAddress
            }
            return localAddress.toSocketAddress()
        }

    override suspend fun accept(): Socket {
        channel.accept()?.let { return accepted(it) }
        return acceptSuspend()
    }

    private suspend fun acceptSuspend(): Socket {
        while (true) {
            interestOp(SelectInterest.ACCEPT, true)
            selector.select(this, SelectInterest.ACCEPT)
            channel.accept()?.let { return accepted(it) }
        }
    }

    private fun accepted(nioChannel: SocketChannel): Socket {
        interestOp(SelectInterest.ACCEPT, false)
        nioChannel.configureBlocking(false)
        if (localAddress is InetSocketAddress) {
            if (java7NetworkApisAvailable) {
                nioChannel.setOption(StandardSocketOptions.TCP_NODELAY, true)
            } else {
                nioChannel.socket().tcpNoDelay = true
            }
        }
        return SocketImpl(nioChannel, selector)
    }

    override fun close() {
        try {
            try {
                channel.close()
            } finally {
                selector.notifyClosed(this)
            }

            socketContext.complete()
        } catch (cause: Throwable) {
            socketContext.completeExceptionally(cause)
        }
    }
}
