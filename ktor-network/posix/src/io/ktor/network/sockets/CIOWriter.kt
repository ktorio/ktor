/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.sockets

import io.ktor.network.selector.*
import io.ktor.network.util.*
import io.ktor.utils.io.*
import io.ktor.utils.io.errors.*
import kotlinx.cinterop.*
import kotlinx.coroutines.*
import kotlinx.io.IOException
import kotlin.math.*

@OptIn(ExperimentalForeignApi::class)
internal fun CoroutineScope.attachForWritingImpl(
    userChannel: ByteChannel,
    descriptor: Int,
    selectable: Selectable,
    selector: SelectorManager
): ReaderJob = reader(Dispatchers.IO, userChannel) {
    val source = channel
    var sockedClosed = false
    var needSelect = false
    var total = 0
    while (!sockedClosed && !source.isClosedForRead) {
        val count = source.read { memory, start, stop ->
            val written = memory.usePinned { pinned ->
                val bufferStart = pinned.addressOf(start).reinterpret<ByteVar>()
                val remaining = stop - start
                val bytesWritten = if (remaining > 0) {
                    ktor_send(descriptor, bufferStart, remaining.convert(), 0).toInt()
                } else {
                    0
                }

                when (bytesWritten) {
                    0 -> sockedClosed = true
                    -1 -> {
                        val error = getSocketError()
                        if (isWouldBlockError(error)) {
                            needSelect = true
                        } else {
                            throw PosixException.forSocketError(error)
                        }
                    }
                }

                bytesWritten
            }

            max(0, written)
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
        source.closedCause?.let { throw it }
    }
}.apply {
    invokeOnCompletion {
        ktor_shutdown(descriptor, ShutdownCommands.Send)
    }
}
