/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.network.sockets

import io.ktor.network.selector.*
import io.ktor.network.util.*
import io.ktor.util.network.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import java.nio.*
import java.nio.channels.*

internal class DatagramSocketImpl(
    override val channel: DatagramChannel,
    selector: SelectorManager
) : BoundDatagramSocket, ConnectedDatagramSocket, NIOSocketImpl<DatagramChannel>(
    channel,
    selector,
    DefaultDatagramByteBufferPool
) {
    private val socket = channel.socket()!!

    override val localAddress: NetworkAddress
        get() = socket.localSocketAddress
            ?: throw IllegalStateException("Channel is not yet bound")

    override val remoteAddress: NetworkAddress
        get() = socket.remoteSocketAddress
            ?: throw IllegalStateException("Channel is not yet connected")

    private val sender: SendChannel<Datagram> = DatagramSendChannel(channel, this)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val receiver: ReceiveChannel<Datagram> = produce(Dispatchers.IO) {
        try {
            while (true) {
                channel.send(receiveImpl())
            }
        } catch (_: ClosedChannelException) {
        }
    }

    override val incoming: ReceiveChannel<Datagram>
        get() = receiver

    override val outgoing: SendChannel<Datagram>
        get() = sender

    override fun close() {
        receiver.cancel()
        super.close()
        sender.close()
    }

    @Suppress("BlockingMethodInNonBlockingContext")
    private suspend fun receiveImpl(): Datagram {
        val buffer = DefaultDatagramByteBufferPool.borrow()
        val address = try {
            channel.receive(buffer)
        } catch (cause: Throwable) {
            DefaultDatagramByteBufferPool.recycle(buffer)
            throw cause
        } ?: return receiveSuspend(buffer)

        interestOp(SelectInterest.READ, false)
        buffer.flip()
        val datagram = Datagram(buildPacket { writeFully(buffer) }, address)
        DefaultDatagramByteBufferPool.recycle(buffer)
        return datagram
    }

    private tailrec suspend fun receiveSuspend(buffer: ByteBuffer): Datagram {
        interestOp(SelectInterest.READ, true)
        selector.select(this, SelectInterest.READ)

        val address = try {
            channel.receive(buffer)
        } catch (cause: Throwable) {
            DefaultDatagramByteBufferPool.recycle(buffer)
            throw cause
        } ?: return receiveSuspend(buffer)

        interestOp(SelectInterest.READ, false)
        buffer.flip()
        val datagram = Datagram(buildPacket { writeFully(buffer) }, address)
        DefaultDatagramByteBufferPool.recycle(buffer)
        return datagram
    }
}
