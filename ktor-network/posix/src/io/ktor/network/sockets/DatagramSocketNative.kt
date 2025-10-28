/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.sockets

import io.ktor.network.selector.*
import io.ktor.network.util.*
import io.ktor.utils.io.core.*
import io.ktor.utils.io.errors.*
import io.ktor.utils.io.pool.*
import kotlinx.cinterop.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.io.IOException
import platform.posix.*
import kotlin.coroutines.*

internal class DatagramSocketNative(
    val selector: SelectorManager,
    descriptor: Int,
    private val remote: SocketAddress?,
    parent: CoroutineContext = EmptyCoroutineContext
) : BoundDatagramSocket, ConnectedDatagramSocket, NativeSocketImpl(
    selector,
    descriptor,
    parent
) {
    override val localAddress: SocketAddress
        get() = getLocalAddress(descriptor).toSocketAddress()

    override val remoteAddress: SocketAddress
        get() = getRemoteAddress(descriptor).toSocketAddress()

    private val sender: SendChannel<Datagram> = DatagramSendChannel(descriptor, this, remote)

    override fun toString(): String = "DatagramSocketNative(descriptor=$descriptor)"

    @OptIn(ExperimentalCoroutinesApi::class)
    private val receiver: ReceiveChannel<Datagram> = produce(Dispatchers.IO) {
        try {
            while (true) {
                val received = readDatagram()
                channel.send(received)
            }
        } catch (_: ClosedSocketException) {
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

    private suspend fun readDatagram(): Datagram {
        while (true) {
            val datagram = tryReadDatagram()
            if (datagram != null) return datagram
            selector.select(this, SelectInterest.READ)
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun tryReadDatagram(): Datagram? = memScoped {
        val clientAddress = alloc<sockaddr_storage>()
        val clientAddressLength: UIntVarOf<UInt> = alloc()
        clientAddressLength.value = sizeOf<sockaddr_storage>().convert()

        DefaultDatagramByteArrayPool.useInstance { buffer ->
            val bytesRead = buffer.usePinned { pinned ->
                ktor_recvfrom(
                    descriptor,
                    pinned.addressOf(0),
                    buffer.size.convert(),
                    0,
                    clientAddress.ptr.reinterpret(),
                    clientAddressLength.ptr
                ).toLong()
            }

            when (bytesRead) {
                0L -> throw ClosedSocketException()
                -1L -> {
                    val error = getSocketError()
                    if (isWouldBlockError(error)) return null
                    if (error == 0) return null
                    throw PosixException.forSocketError(error)
                }
            }

            val address = clientAddress.reinterpret<sockaddr>().toNativeSocketAddress()

            Datagram(
                buildPacket { writeFully(buffer, length = bytesRead.toInt()) },
                address.toSocketAddress()
            )
        }
    }
}

private class ClosedSocketException : IOException()
