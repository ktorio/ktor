/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.utils.io

import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlinx.io.IOException
import kotlinx.io.RawSink
import kotlinx.io.Sink
import kotlinx.io.buffered

/**
 * Creates a [ByteWriteChannel] that writes to this [Sink].
 *
 * Example usage:
 * ```kotlin
 * suspend fun writeMessage(raw: RawSink) {
 *     val channel = raw.asByteWriteChannel()
 *     channel.writeByte(42)
 *     channel.flushAndClose()
 * }
 *
 * val buffer = Buffer()
 * writeMessage(buffer)
 * buffer.readByte() // 42
 * ```
 *
 * Please note that the channel will be buffered even if the sink is not.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.utils.io.asByteWriteChannel)
 */
public fun RawSink.asByteWriteChannel(): ByteWriteChannel = SinkByteWriteChannel(this)

internal class SinkByteWriteChannel(origin: RawSink) : ByteWriteChannel {
    val closed: AtomicRef<CloseToken?> = atomic(null)
    private val buffer = origin.buffered()

    override val isClosedForWrite: Boolean
        get() = closed.value != null

    override val closedCause: Throwable?
        get() = closed.value?.wrapCause()

    @InternalAPI
    override val writeBuffer: Sink
        get() {
            if (isClosedForWrite) throw closedCause ?: IOException("Channel is closed for write")
            return buffer
        }

    @OptIn(InternalAPI::class)
    override suspend fun flush() {
        writeBuffer.flush()
    }

    @OptIn(InternalAPI::class)
    override suspend fun flushAndClose() {
        writeBuffer.flush()
        if (!closed.compareAndSet(expect = null, update = CLOSED)) return
    }

    override fun cancel(cause: Throwable?) {
        val token = if (cause == null) CLOSED else CloseToken(cause)
        if (!closed.compareAndSet(expect = null, update = token)) return
    }
}
