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

@OptIn(ExperimentalForeignApi::class)
internal fun CoroutineScope.attachForReadingImpl(
    userChannel: ByteChannel,
    descriptor: Int,
    selectable: Selectable,
    selector: SelectorManager
): WriterJob = writer(Dispatchers.IO, userChannel) {
    try {
        while (!channel.isClosedForWrite) {
            var close = false
            val count = channel.write { memory, startIndex, endIndex ->
                memory.usePinned {
                    val bufferStart = it.addressOf(startIndex)
                    val size = endIndex - startIndex
                    val bytesRead = ktor_recv(descriptor, bufferStart, size.convert(), 0).toInt()

                    when (bytesRead) {
                        0 -> close = true
                        -1 -> {
                            val error = getSocketError()
                            if (isWouldBlockError(error)) return@write 0
                            if (error == 0) return@write 0
                            throw PosixException.forSocketError(error)
                        }
                    }

                    bytesRead
                }
            }

            channel.flush()
            if (close) {
                channel.flushAndClose()
                break
            }

            if (count == 0) {
                try {
                    selector.select(selectable, SelectInterest.READ)
                } catch (_: IOException) {
                    break
                }
            }
        }

        channel.closedCause?.let { throw it }
    } catch (cause: Throwable) {
        channel.close(cause)
        throw cause
    } finally {
        ktor_shutdown(descriptor, ShutdownCommands.Receive)
        channel.flushAndClose()
    }
}
