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
) : ServerSocket, Selectable by SelectableBase(channel) {
    init {
        require(!channel.isBlocking) { "Channel need to be configured as non-blocking." }
    }

    override val socketContext: CompletableJob = Job()

    override val localAddress: SocketAddress
        get() = channel.localAddress.toSocketAddress()

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
        if (localAddress is InetSocketAddress) nioChannel.setOption(StandardSocketOptions.TCP_NODELAY, true)
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

    override fun dispose() {
        super.dispose()
    }
}
