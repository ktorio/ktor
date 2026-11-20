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
                val bytesWritten = sendto(element, buffer, 0, 0)
                if (bytesWritten == -1) {
                    val error = getSocketError()
                    if (isWouldBlockError(error)) {
                        false
                    } else {
                        throw PosixException.forSocketError(error)
                    }
                } else {
                    true
                }
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

            val bytesWritten = sendto(element, bytes, startIndex, length)

            when (bytesWritten) {
                0 -> throw IOException("Failed writing to closed socket")

                -1 -> {
                    val error = getSocketError()
                    if (isWouldBlockError(error)) {
                        0
                    } else {
                        throw PosixException.forSocketError(error)
                    }
                }

                else -> length
            }
        }
        if (writeWithPool) {
            DefaultDatagramByteArrayPool.useInstance { buffer ->
                val length = element.packet.remaining.toInt()
                element.packet.peek().readTo(buffer, endIndex = length)

                val bytesWritten = sendto(element, buffer, 0, length)

                when (bytesWritten) {
                    0 -> throw IOException("Failed writing to closed socket")

                    -1 -> {
                        val error = getSocketError()
                        if (isWouldBlockError(error)) {
                            // would block: drop on best-effort trySend
                        } else {
                            throw PosixException.forSocketError(error)
                        }
                    }

                    else -> {
                        element.packet.discard()
                    }
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

    @OptIn(ExperimentalForeignApi::class)
    private fun sendto(datagram: Datagram, buffer: ByteArray, offset: Int, length: Int): Int {
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
        return bytesWritten ?: error("bytesWritten cannot be null")
    }

    private tailrec suspend fun sendSuspend(
        datagram: Datagram,
        buffer: ByteArray,
        offset: Int,
        length: Int
    ) {
        val bytesWritten: Int = sendto(datagram, buffer, offset, length)

        when (bytesWritten) {
            0 -> {
                if (length != 0) {
                    throw IOException("Failed writing to closed socket")
                }
            }

            -1 -> {
                val error = getSocketError()
                if (isWouldBlockError(error)) {
                    socket.selector.select(socket, SelectInterest.WRITE)
                    sendSuspend(datagram, buffer, offset, length)
                } else {
                    throw PosixException.forSocketError(error)
                }
            }
        }
    }
}
