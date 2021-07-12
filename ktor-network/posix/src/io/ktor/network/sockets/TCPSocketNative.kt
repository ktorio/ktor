/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.network.sockets

import io.ktor.network.selector.*
import io.ktor.util.network.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import platform.posix.*
import kotlin.coroutines.*

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

    override fun attachForReading(channel: ByteChannel): WriterJob =
        attachForReadingImpl(channel, descriptor, selectable, selector)

    override fun attachForWriting(channel: ByteChannel): ReaderJob =
        attachForWritingImpl(channel, descriptor, selectable, selector)

    override fun close() {
        _context.complete()
        selector.notifyClosed(selectable)
        _context.invokeOnCompletion {
            shutdown(descriptor, SHUT_RDWR)
            // Descriptor is closed by the selector manager
        }
    }
}
