/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.network.sockets

import io.ktor.network.selector.*
import io.ktor.network.util.*
import io.ktor.utils.io.core.*
import io.ktor.utils.io.pool.*
import kotlinx.coroutines.*
import kotlinx.io.*
import kotlinx.io.unsafe.*
import java.nio.*
import java.nio.channels.*

internal class DatagramSendChannel(
    private val channel: DatagramChannel,
    private val socket: DatagramSocketImpl,
) : DatagramSendChannelBase(socket, remote = null /*JDK handles it internally*/) {
    @OptIn(InternalIoApi::class, UnsafeIoApi::class)
    override fun trySendImpl(element: Datagram): Boolean {
        val packetSize = element.packet.remaining
        var writeWithPool = false
        UnsafeBufferOperations.readFromHead(element.packet.buffer) { buffer ->
            val length = buffer.remaining()
            if (length < packetSize) {
                // Packet is too large to read directly.
                writeWithPool = true
                return@readFromHead
            }

            val result = channel.send(buffer, element.address.toJavaAddress()) == 0
            if (result) {
                buffer.position(buffer.limit())
            } else {
                buffer.position(0)
            }
        }
        if (writeWithPool) {
            DefaultDatagramByteBufferPool.useInstance { buffer ->
                element.packet.peek().writeMessageTo(buffer)

                val result = channel.send(buffer, element.address.toJavaAddress()) == 0
                if (result) {
                    element.packet.discard()
                }
            }
        }
        return true
    }

    @OptIn(InternalIoApi::class, UnsafeIoApi::class)
    override suspend fun sendImpl(element: Datagram) {
        withContext(Dispatchers.IO) {
            val packetSize = element.packet.remaining
            var writeWithPool = false
            UnsafeBufferOperations.readFromHead(element.packet.buffer) { buffer ->
                val length = buffer.remaining()
                if (length < packetSize) {
                    // Packet is too large to read directly.
                    writeWithPool = true
                    return@readFromHead
                }

                val rc = channel.send(buffer, element.address.toJavaAddress())
                if (rc != 0) {
                    socket.interestOp(SelectInterest.WRITE, false)
                    buffer.position(buffer.limit()) // consume all data
                    return@readFromHead
                }

                sendSuspend(buffer, element.address)
                buffer.position(buffer.limit()) // consume all data
            }
            if (writeWithPool) {
                DefaultDatagramByteBufferPool.useInstance { buffer ->
                    element.packet.writeMessageTo(buffer)

                    val rc = channel.send(buffer, element.address.toJavaAddress())
                    if (rc != 0) {
                        socket.interestOp(SelectInterest.WRITE, false)
                        return@useInstance
                    }

                    sendSuspend(buffer, element.address)
                }
            }
        }
    }

    private suspend fun sendSuspend(buffer: ByteBuffer, address: SocketAddress) {
        while (true) {
            socket.interestOp(SelectInterest.WRITE, true)
            socket.selector.select(socket, SelectInterest.WRITE)

            @Suppress("BlockingMethodInNonBlockingContext")
            // this is actually a non-blocking invocation
            if (channel.send(buffer, address.toJavaAddress()) != 0) {
                socket.interestOp(SelectInterest.WRITE, false)
                break
            }
        }
    }

    private fun Source.writeMessageTo(buffer: ByteBuffer) {
        readFully(buffer)
        buffer.flip()
    }
}
