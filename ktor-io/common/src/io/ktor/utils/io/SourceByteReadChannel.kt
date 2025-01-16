/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.utils.io

import io.ktor.utils.io.core.*
import kotlinx.io.*
import kotlin.concurrent.*

internal class SourceByteReadChannel(private val source: Source) : ByteReadChannel {
    @Volatile
    private var closed: CloseToken? = null

    override val closedCause: Throwable?
        get() = closed?.wrapCause()

    override val isClosedForRead: Boolean
        get() = source.exhausted()

    @InternalAPI
    override val readBuffer: Source
        get() {
            closedCause?.let { throw it }
            return source
        }

    override suspend fun awaitContent(min: Int): Boolean {
        closedCause?.let { throw it }
        return source.remaining >= min
    }

    override fun cancel(cause: Throwable?) {
        if (closed != null) return
        source.close()
        closed = CloseToken(IOException(cause?.message ?: "Channel was cancelled", cause))
    }
}
