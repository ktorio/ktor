/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.utils.io

import io.ktor.utils.io.core.*
import kotlinx.io.*

public fun ByteWriteChannel.counted(): CountedByteWriteChannel = CountedByteWriteChannel(this)

public class CountedByteWriteChannel(private val delegate: ByteWriteChannel) : ByteWriteChannel {
    @OptIn(InternalKtorApi::class)
    private var initial = delegate.writeBuffer.size
    private var flushedCount = 0

    public override val autoFlush: Boolean
        get() = delegate.autoFlush

    @OptIn(InternalKtorApi::class)
    public val totalBytesWritten: Long get() = (flushedCount + writeBuffer.size - initial).toLong()

    override val isClosedForWrite: Boolean
        get() = delegate.isClosedForWrite
    override val closedCause: Throwable?
        get() = delegate.closedCause

    @InternalKtorApi
    override val writeBuffer: Sink
        get() = delegate.writeBuffer

    @OptIn(InternalKtorApi::class)
    override suspend fun flush() {
        flushedCount += writeBuffer.size
        delegate.flush()
        initial = writeBuffer.size
    }

    override suspend fun flushAndClose() {
        delegate.flushAndClose()
    }

    override fun cancel(cause: Throwable?) {
        delegate.cancel(cause)
    }
}
