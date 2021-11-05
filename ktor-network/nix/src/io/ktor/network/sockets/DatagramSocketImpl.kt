/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.sockets

import io.ktor.network.selector.*
import io.ktor.network.util.*
import io.ktor.util.network.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import io.ktor.utils.io.core.internal.*
import io.ktor.utils.io.errors.*
import kotlinx.cinterop.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import platform.posix.*
import kotlin.coroutines.*

internal class DatagramSocketImpl(
    private val descriptor: Int,
    val selector: SelectorManager,
    parent: CoroutineContext = EmptyCoroutineContext
) : BoundDatagramSocket, ConnectedDatagramSocket, Socket, CoroutineScope {
    private val _context: CompletableJob = Job(parent[Job])
    val selectable: SelectableNative = SelectableNative(descriptor)

    override val coroutineContext: CoroutineContext = parent + Dispatchers.Unconfined + _context

    override val socketContext: Job
        get() = _context

    override val localAddress: NetworkAddress
        get() = getLocalAddress(descriptor).let { ResolvedNetworkAddress(it.address, it.port, it) }
    override val remoteAddress: NetworkAddress
        get() = getRemoteAddress(descriptor).let { ResolvedNetworkAddress(it.address, it.port, it) }

    private val sender: SendChannel<Datagram> = DatagramSendChannel(descriptor, this)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val receiver: ReceiveChannel<Datagram> = produce {
        try {
            while (true) {
                val received = receiveImpl()
                channel.send(received)
            }
        } catch (_: ClosedSendChannelException) {
        }
    }

    override val incoming: ReceiveChannel<Datagram>
        get() = receiver

    override val outgoing: SendChannel<Datagram>
        get() = sender

    init {
        makeShared()
    }

    override fun close() {
        receiver.cancel()
        _context.complete()
        _context.invokeOnCompletion {
            shutdown(descriptor, SHUT_RDWR)
            // Descriptor is closed by the selector manager
            selector.notifyClosed(selectable)
        }
        sender.close()
    }

    override fun attachForReading(channel: ByteChannel): WriterJob =
        attachForReadingImpl(channel, descriptor, selectable, selector)

    override fun attachForWriting(channel: ByteChannel): ReaderJob =
        attachForWritingImpl(channel, descriptor, selectable, selector)

    private tailrec suspend fun receiveImpl(
        buffer: ChunkBuffer = DefaultDatagramChunkBufferPool.borrow()
    ): Datagram {
        memScoped {
            val clientAddress = alloc<sockaddr_storage>()
            val clientAddressLength: UIntVarOf<UInt> = alloc()
            clientAddressLength.value = sizeOf<sockaddr_storage>().convert()

            val count = try {
                buffer.write { memory, startIndex, endIndex ->
                    val bufferStart = memory.pointer + startIndex
                    val size = endIndex - startIndex
                    val bytesRead = recvfrom(
                        descriptor,
                        bufferStart,
                        size.convert(),
                        0,
                        clientAddress.ptr.reinterpret(),
                        clientAddressLength.ptr
                    ).toInt()

                    when (bytesRead) {
                        0 -> throw IOException("Failed reading from closed socket")
                        -1 -> {
                            if (errno == EAGAIN) {
                                return@write 0
                            }
                            throw PosixException.forErrno()
                        }
                    }

                    bytesRead
                }
            } catch (cause: Throwable) {
                buffer.release(DefaultDatagramChunkBufferPool)
                throw cause
            }
            if (count > 0) {
                val address = clientAddress.reinterpret<sockaddr>().toSocketAddress()
                val datagram = Datagram(
                    buildPacket { writeFully(buffer) },
                    ResolvedNetworkAddress(address.address, address.port, address)
                )
                buffer.release(DefaultDatagramChunkBufferPool)
                return datagram
            } else {
                selector.select(selectable, SelectInterest.READ)
                return receiveImpl(buffer)
            }
        }
    }
}
