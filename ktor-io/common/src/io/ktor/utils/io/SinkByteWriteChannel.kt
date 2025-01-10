/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.utils.io

import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlinx.io.*

/**
 * Creates a [ByteWriteChannel] that writes to this [Sink].
 *
 * Example usage:
 * ```kotlin
 * val rawSink: RawSink = Buffer()
 * val channel: ByteWriteChannel = rawSink.asByteWriteChannel()
 * channel.writeString("hello, world!")
 * channel.flushAndClose()

 * (rawSink as Buffer).readString() // "Hello, world"
 * ```
 *
 * Please note that the channel will be buffered even if the sync is not.
 */
public fun RawSink.asByteWriteChannel(): ByteWriteChannel = SinkByteWriteChannel(this).apply {
}

private suspend fun x() {
    val rawSink: RawSink = Buffer()
    val channel: ByteWriteChannel = rawSink.asByteWriteChannel()
    channel.writeString("hello, world!")
    channel.flushAndClose()

    (rawSink as Buffer).readString() // "Hello, world"
}

internal class SinkByteWriteChannel(private val origin: RawSink) : ByteWriteChannel {
    val closed: AtomicRef<CloseToken?> = atomic(null)

    override val isClosedForWrite: Boolean
        get() = closed.value != null

    override val closedCause: Throwable?
        get() = closed.value?.cause

    @InternalAPI
    override val writeBuffer: Sink
        get() {
            if (isClosedForWrite) throw closedCause ?: IOException("Channel is closed for write")
            return origin as? Sink ?: origin.buffered()
        }

    @OptIn(InternalAPI::class)
    override suspend fun flush() {
        writeBuffer.flush()
    }

    @OptIn(InternalAPI::class)
    override suspend fun flushAndClose() {
        if (!closed.compareAndSet(expect = null, update = CLOSED)) return
        writeBuffer.flush()
    }

    @OptIn(InternalAPI::class)
    override fun cancel(cause: Throwable?) {
        val token = if (cause == null) CLOSED else CloseToken(cause)
        if (!closed.compareAndSet(expect = null, update = token)) return
    }
}
