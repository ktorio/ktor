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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.IO
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.produce
import kotlinx.io.IOException
import platform.posix.sockaddr
import platform.posix.sockaddr_storage
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

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
        } catch (_: ClosedSendChannelException) {
        } catch (cause: IOException) {
        } catch (cause: PosixException) {
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
            DefaultDatagramByteArrayPool.useInstance { buffer ->
                val result = tryReadDatagram(buffer)
                if (result != null) {
                    return Datagram(
                        buildPacket { writeFully(buffer, length = result.byteCount) },
                        result.senderAddress
                    )
                }
            }
            selector.select(this, SelectInterest.READ)
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun tryReadDatagram(buffer: ByteArray): DatagramReceiveResult? = memScoped {
        val clientAddress = alloc<sockaddr_storage>()
        val clientAddressLength: UIntVarOf<UInt> = alloc()
        clientAddressLength.value = sizeOf<sockaddr_storage>().convert()

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
            0L -> throw IOException("Failed reading from closed socket")

            -1L -> {
                val error = getSocketError()
                if (isWouldBlockError(error)) return null
                if (error == 0) return null
                throw PosixException.forSocketError(error)
            }
        }

        val address = clientAddress.reinterpret<sockaddr>().toNativeSocketAddress()
        DatagramReceiveResult(
            byteCount = bytesRead.toInt(),
            senderAddress = address.toSocketAddress()
        )
    }
}

private data class DatagramReceiveResult(val byteCount: Int, val senderAddress: SocketAddress)
