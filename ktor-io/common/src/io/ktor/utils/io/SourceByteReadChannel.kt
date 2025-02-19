/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.utils.io

import kotlinx.io.IOException
import kotlinx.io.InternalIoApi
import kotlinx.io.Source
import kotlin.concurrent.Volatile

internal class SourceByteReadChannel(private val source: Source) : ByteReadChannel {
    @Volatile
    private var closed: CloseToken? = null

    override val closedCause: Throwable?
        get() = closed?.wrapCause()

    override val isClosedForRead: Boolean
        get() = source.exhausted()

    @OptIn(InternalIoApi::class)
    @InternalAPI
    override val readBuffer: Source
        get() {
            closedCause?.let { throw it }
            return source.buffer
        }

    override suspend fun awaitContent(min: Int): Boolean {
        closedCause?.let { throw it }
        return source.request(min.toLong())
    }

    override fun cancel(cause: Throwable?) {
        if (closed != null) return
        source.close()
        closed = CloseToken(IOException(cause?.message ?: "Channel was cancelled", cause))
    }
}
