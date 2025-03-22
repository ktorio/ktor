/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.utils.io

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CopyableThrowable
import kotlinx.coroutines.ExperimentalCoroutinesApi

internal val CLOSED = CloseToken(null)

@OptIn(ExperimentalCoroutinesApi::class)
internal class CloseToken(private val origin: Throwable?) {

    fun wrapCause(wrap: (Throwable) -> Throwable = ::ClosedByteChannelException): Throwable? {
        return when (origin) {
            null -> null
            is CopyableThrowable<*> -> origin.createCopy()
            is CancellationException -> CancellationException(origin.message, origin)
            else -> wrap(origin)
        }
    }

    fun throwOrNull(wrap: (Throwable) -> Throwable): Unit? =
        wrapCause(wrap)?.let { throw it }
}
