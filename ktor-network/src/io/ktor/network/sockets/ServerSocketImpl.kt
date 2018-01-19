package io.ktor.network.sockets

import io.ktor.network.selector.*
import kotlinx.coroutines.experimental.*
import java.net.*
import java.nio.channels.*

internal class ServerSocketImpl(override val channel: ServerSocketChannel, val selector: SelectorManager)
    : ServerSocket,
        Selectable by SelectableBase(channel) {
    init {
        require(!channel.isBlocking)
    }

    override val socketContext = CompletableDeferred<Unit>()

    override val localAddress: SocketAddress
        get() = channel.localAddress

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
        nioChannel.setOption(StandardSocketOptions.TCP_NODELAY, true)
        return SocketImpl(nioChannel, selector)
    }

    override fun close() {
        try {
            try {
                channel.close()
            } finally {
                selector.notifyClosed(this)
            }

            socketContext.complete(Unit)
        } catch (t: Throwable) {
            socketContext.completeExceptionally(t)
        }
    }

    override fun dispose() {
        super.dispose()
    }
}