/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.network.sockets

import io.ktor.network.selector.*
import io.ktor.util.*
import io.ktor.util.network.NetworkAddress
import io.ktor.utils.io.*
import io.ktor.utils.io.errors.*
import kotlinx.cinterop.*
import kotlinx.coroutines.*
import platform.posix.*
import kotlin.coroutines.*
import kotlin.math.*

internal class TCPSocketNative(
    private val descriptor: Int,
    private val selector: SelectorManager,
    override val remoteAddress: NetworkAddress,
    override val localAddress: NetworkAddress,
    parent: CoroutineContext = EmptyCoroutineContext
) : Socket, CoroutineScope {
    private val _context: CompletableJob = Job(parent[Job])
    private val selectable: SelectableNative = SelectableNative(descriptor)

    override val coroutineContext: CoroutineContext = parent + Dispatchers.Unconfined + _context

    override val socketContext: Job
        get() = _context

    init {
        makeShared()
    }

    override fun attachForReading(userChannel: ByteChannel): WriterJob = writer(Dispatchers.Unconfined, userChannel) {
        while (!channel.isClosedForWrite) {
            val count = channel.write { memory, startIndex, endIndex ->
                val bufferStart = memory.pointer + startIndex
                val size = endIndex - startIndex
                val result = recv(descriptor, bufferStart, size.convert(), 0).toInt()

                if (result == 0) {
                    channel.close()
                }
                if (result == -1) {
                    if (errno == EAGAIN) {
                        return@write 0
                    }

                    throw PosixException.forErrno()
                }

                result.convert()
            }

            if (count == 0 && !channel.isClosedForWrite) {
                selector.select(selectable, SelectInterest.READ)
            }

            channel.flush()
        }

        channel.closedCause?.let { throw it }
    }.apply {
        invokeOnCompletion {
            shutdown(descriptor, SHUT_RD)
        }
    }

    override fun attachForWriting(userChannel: ByteChannel): ReaderJob = reader(Dispatchers.Unconfined, userChannel) {
        val source = channel
        var sockedClosed = false
        var needSelect = false
        var total = 0
        while (!sockedClosed && !source.isClosedForRead) {
            val count = source.read { memory, start, stop ->
                val bufferStart = memory.pointer + start
                val remaining = stop - start
                val result = if (remaining > 0) {
                    send(descriptor, bufferStart, remaining.convert(), 0).toInt()
                } else 0

                when (result) {
                    0 -> sockedClosed = true
                    -1 -> {
                        if (errno == EAGAIN) {
                            needSelect = true
                        } else {
                            throw PosixException.forErrno()
                        }
                    }
                }

                max(0, result)
            }

            total += count
            if (!sockedClosed && needSelect) {
                selector.select(selectable, SelectInterest.WRITE)
                needSelect = false
            }
        }

        if (!source.isClosedForRead) {
            val availableForRead = source.availableForRead
            val cause = IOException("Failed writing to closed socket. Some bytes remaining: $availableForRead")
            source.cancel(cause)
        } else {
            if (source is ByteChannel) {
                source.closedCause?.let { throw it }
            }
        }
    }.apply {
        invokeOnCompletion {
            shutdown(descriptor, SHUT_WR)
        }
    }

    override fun close() {
        _context.complete()
        _context.invokeOnCompletion {
            shutdown(descriptor, SHUT_RDWR)
            close(descriptor)
        }
    }
}
