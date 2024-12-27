/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.selector

import kotlinx.atomicfu.*
import java.nio.channels.*
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.*

internal open class SelectableBase(override val channel: SelectableChannel) : Selectable {
    private val _isClosed = AtomicBoolean(false)

    override val suspensions = InterestSuspensionsMap()

    override val isClosed: Boolean
        get() = _isClosed.get()

    private val _interestedOps = atomic(0)

    override val interestedOps: Int get() = _interestedOps.value

    override fun interestOp(interest: SelectInterest, state: Boolean) {
        val flag = interest.flag

        while (true) {
            val before = _interestedOps.value
            val after = if (state) before or flag else before and flag.inv()
            if (_interestedOps.compareAndSet(before, after)) break
        }
    }

    override fun close() {
        if (!_isClosed.compareAndSet(false, true)) return

        _interestedOps.value = 0
        suspensions.invokeForEachPresent {
            resumeWithException(ClosedChannelCancellationException())
        }
    }

    override fun dispose() {
        close()
    }
}
