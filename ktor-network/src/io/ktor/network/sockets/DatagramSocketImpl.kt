package io.ktor.network.sockets

import io.ktor.network.selector.*
import io.ktor.network.util.*
import kotlinx.coroutines.experimental.channels.*
import kotlinx.io.core.*
import java.net.*
import java.nio.*
import java.nio.channels.*

internal class DatagramSocketImpl(override val channel: DatagramChannel, selector: SelectorManager)
    : BoundDatagramSocket, ConnectedDatagramSocket, NIOSocketImpl<DatagramChannel>(channel, selector, DefaultDatagramByteBufferPool) {

    override val localAddress: SocketAddress
        get() = channel.localAddress ?: throw IllegalStateException("Channel is not yet bound")

    override val remoteAddress: SocketAddress
        get() = channel.remoteAddress ?: throw IllegalStateException("Channel is not yet connected")

    private val sender = actor<Datagram>(ioCoroutineDispatcher) {
        consumeEach { datagram ->
            sendImpl(datagram)
        }
    }

    private val receiver = produce<Datagram>(ioCoroutineDispatcher) {
        while (true) {
            channel.send(receiveImpl())
        }
    }

    override val incoming: ReceiveChannel<Datagram>
        get() = receiver

    override val outgoing: SendChannel<Datagram>
        get() = sender

    override fun close() {
        receiver.cancel()
        sender.close()
        super.close()
    }

    private suspend fun receiveImpl(): Datagram {
        val buffer = DefaultDatagramByteBufferPool.borrow()
        val address = try {
            channel.receive(buffer)
        } catch (t: Throwable) {
            DefaultDatagramByteBufferPool.recycle(buffer)
            throw t
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
        } catch (t: Throwable) {
            DefaultDatagramByteBufferPool.recycle(buffer)
            throw t
        }

        if (address == null) return receiveSuspend(buffer)

        interestOp(SelectInterest.READ, false)
        buffer.flip()
        val datagram = Datagram(buildPacket { writeFully(buffer) }, address)
        DefaultDatagramByteBufferPool.recycle(buffer)
        return datagram
    }

    private suspend fun sendImpl(datagram: Datagram) {
        val buffer = ByteBuffer.allocateDirect(datagram.packet.remaining.toInt())
        datagram.packet.readAvailable(buffer)
        buffer.flip()

        val rc = channel.send(buffer, datagram.address)
        if (rc == 0) {
            sendSuspend(buffer, datagram.address)
        } else {
            interestOp(SelectInterest.WRITE, false)
        }
    }

    private tailrec suspend fun sendSuspend(buffer: ByteBuffer, address: SocketAddress) {
        interestOp(SelectInterest.WRITE, true)
        selector.select(this, SelectInterest.WRITE)

        if (channel.send(buffer, address) == 0) sendSuspend(buffer, address)
        else interestOp(SelectInterest.WRITE, false)
    }
}
