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
    private var count = 0L
    private var initial = delegate.availableForRead

    public val totalBytesRead: Long
        get() = count + (initial - delegate.availableForRead).toLong()

    override val closedCause: Throwable?
        get() = delegate.closedCause

    override val isClosedForRead: Boolean
        get() = delegate.isClosedForRead

    @InternalAPI
    override val readBuffer: Source
        get() = delegate.readBuffer

    override suspend fun awaitContent(min: Int): Boolean {
        val before = delegate.availableForRead
        val result = delegate.awaitContent(min)
        count += (initial - before).toLong()
        initial = delegate.availableForRead
        return result
    }

    override fun cancel(cause: Throwable?) {
        delegate.cancel(cause)
    }
}
