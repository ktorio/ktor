/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.network.selector

import kotlinx.coroutines.CancellableContinuation
import kotlin.coroutines.*

internal data class EventInfo(
    val descriptor: Int,
    val interest: SelectInterest,
    private val continuation: Continuation<Unit>
) {
    val isActive: Boolean
        get() = (continuation as? CancellableContinuation<*>)?.isActive ?: true

    fun complete() {
        continuation.resume(Unit)
    }

    fun fail(cause: Throwable) {
        continuation.resumeWithException(cause)
    }

    override fun toString(): String = "EventInfo[$descriptor, $interest]"
}
