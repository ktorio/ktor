/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.utils.io

import kotlinx.io.*

@Deprecated(
    "Counter is no longer available on the regular ByteReadChannel. Use CounterByteReadChannel instead.",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("this.counted().totalBytesRead")
)
public val ByteReadChannel.totalBytesRead: Long
    get() = error("Counter is no longer available on the regular ByteReadChannel. Use CounterByteReadChannel instead.")

public fun ByteReadChannel.counted(): CountedByteReadChannel = CountedByteReadChannel(this)

public class CountedByteReadChannel(public val delegate: ByteReadChannel) : ByteReadChannel {
    private val buffer = Buffer()
    private var initial = 0L
    private var consumed = 0L

    public val totalBytesRead: Long
        get() {
            updateConsumed()
            return consumed
        }

    override val closedCause: Throwable?
        get() = delegate.closedCause

    override val isClosedForRead: Boolean
        get() = buffer.exhausted() && delegate.isClosedForRead

    @InternalAPI
    override val readBuffer: Buffer
        get() {
            transferFromDelegate()
            return buffer
        }

    @OptIn(InternalAPI::class)
    override suspend fun awaitContent(min: Int): Boolean {
        if (readBuffer.size >= min) {
            return true
        }
        if (delegate.awaitContent(min)) {
            transferFromDelegate()
            return true
        }
        return false
    }

    @OptIn(InternalAPI::class)
    private fun transferFromDelegate() {
        updateConsumed()
        val appended = buffer.transferFrom(delegate.readBuffer)
        initial += appended
    }

    override fun cancel(cause: Throwable?) {
        delegate.cancel(cause)
        buffer.close()
    }

    private fun updateConsumed() {
        consumed += initial - buffer.size
        initial = buffer.size
    }
}
