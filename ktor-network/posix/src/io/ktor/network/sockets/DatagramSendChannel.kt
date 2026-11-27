/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.sockets

import io.ktor.network.selector.*
import io.ktor.network.util.*
import io.ktor.utils.io.core.*
import io.ktor.utils.io.errors.*
import io.ktor.utils.io.pool.*
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.io.IOException
import kotlinx.io.InternalIoApi
import kotlinx.io.UnsafeIoApi
import kotlinx.io.readTo
import kotlinx.io.unsafe.UnsafeBufferOperations

internal class DatagramSendChannel(
    private val descriptor: Int,
    private val socket: DatagramSocketNative,
    remote: SocketAddress?,
) : DatagramSendChannelBase(socket, remote) {
    @OptIn(InternalIoApi::class, UnsafeIoApi::class)
    override fun trySendImpl(element: Datagram): Boolean {
        val packetSize = element.packet.remaining
        if (packetSize == 0L) {
            return DefaultDatagramByteArrayPool.useInstance { buffer ->
                sendto(element, buffer, 0, 0) != WOULD_BLOCK
            }
        }
        var writeWithPool = false
        UnsafeBufferOperations.readFromHead(element.packet.buffer) { bytes, startIndex, endIndex ->
            val length = endIndex - startIndex
            if (length < packetSize) {
                // Packet is too large to read directly.
                writeWithPool = true
                return@readFromHead 0
            }

            when (sendto(element, bytes, startIndex, length)) {
                0 -> throw IOException("Failed writing to closed socket")
                WOULD_BLOCK -> 0
                else -> length
            }
        }
        if (writeWithPool) {
            DefaultDatagramByteArrayPool.useInstance { buffer ->
                val length = element.packet.remaining.toInt()
                element.packet.peek().readTo(buffer, endIndex = length)

                when (sendto(element, buffer, 0, length)) {
                    0 -> throw IOException("Failed writing to closed socket")

                    WOULD_BLOCK -> {
                        // would block: drop on best-effort trySend
                    }

                    else -> element.packet.discard()
                }
            }
        }
        return true
    }

    @OptIn(InternalIoApi::class, UnsafeIoApi::class)
    override suspend fun sendImpl(element: Datagram) {
        withContext(Dispatchers.IO) {
            val packetSize = element.packet.remaining
            if (packetSize == 0L) {
                DefaultDatagramByteArrayPool.useInstance { buffer ->
                    sendSuspend(element, buffer, 0, 0)
                }
                return@withContext
            }
            var writeWithPool = false
            UnsafeBufferOperations.readFromHead(element.packet.buffer) { bytes, startIndex, endIndex ->
                val length = endIndex - startIndex
                if (length < packetSize) {
                    // Packet is too large to read directly.
                    writeWithPool = true
                    return@readFromHead 0
                }
                sendSuspend(element, bytes, startIndex, length)
                length
            }
            if (writeWithPool) {
                DefaultDatagramByteArrayPool.useInstance { buffer ->
                    val length = element.packet.remaining.toInt()
                    element.packet.readTo(buffer, endIndex = length)

                    sendSuspend(element, buffer, 0, length)
                }
            }
        }
    }

    /**
     * Returns the number of bytes sent, or [WOULD_BLOCK] if the socket is not writable.
     * The socket error is read before the descriptor is released, because closing the socket can overwrite it.
     *
     * @throws PosixException if the send failed for any other reason.
     * @throws IOException if the descriptor is already closed.
     */
    @OptIn(ExperimentalForeignApi::class)
    private fun sendto(datagram: Datagram, buffer: ByteArray, offset: Int, length: Int): Int = socket.useDescriptor {
        var bytesWritten: Int? = null
        buffer.usePinned { pinned ->
            if (remote == null) {
                datagram.address.address.nativeAddress { address, addressSize ->
                    bytesWritten = ktor_sendto(
                        descriptor,
                        pinned.addressOf(offset),
                        length.convert(),
                        0,
                        address,
                        addressSize
                    )
                }
            } else {
                bytesWritten = ktor_sendto(
                    descriptor,
                    pinned.addressOf(offset),
                    length.convert(),
                    0,
                    null,
                    0.convert()
                )
            }
        }
        val result = bytesWritten ?: error("bytesWritten cannot be null")
        if (result == -1) {
            val error = getSocketError()
            if (!isWouldBlockError(error)) throw PosixException.forSocketError(error)
        }
        result
    }

    private tailrec suspend fun sendSuspend(
        datagram: Datagram,
        buffer: ByteArray,
        offset: Int,
        length: Int
    ) {
        when (sendto(datagram, buffer, offset, length)) {
            0 -> {
                if (length != 0) {
                    throw IOException("Failed writing to closed socket")
                }
            }

            WOULD_BLOCK -> {
                socket.selector.select(socket, SelectInterest.WRITE)
                sendSuspend(datagram, buffer, offset, length)
            }
        }
    }
}

private const val WOULD_BLOCK = -1
