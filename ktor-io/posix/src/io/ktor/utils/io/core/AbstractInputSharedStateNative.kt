// ktlint-disable filename
/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.utils.io.core

import io.ktor.utils.io.bits.*
import io.ktor.utils.io.core.internal.*
import kotlinx.atomicfu.*

internal actual class AbstractInputSharedState actual constructor(
    head: ChunkBuffer,
    remaining: Long
) {
    private val _head: AtomicRef<ChunkBuffer> = atomic(head)
    private val _headMemory: AtomicRef<Memory> = atomic(head.memory)
    private val _headPosition: AtomicInt = atomic(head.readPosition)
    private val _headEndExclusive: AtomicInt = atomic(head.writePosition)
    private val _tailRemaining: AtomicLong = atomic(remaining - (_headEndExclusive.value - _headPosition.value))

    actual var head: ChunkBuffer
        get() = _head.value
        set(value) {
            _head.value = value
        }

    actual var tailRemaining: Long
        get() = _tailRemaining.value
        set(value) {
            _tailRemaining.value = value
        }

    actual var headMemory: Memory
        get() = _headMemory.value
        set(value) {
            _headMemory.value = value
        }

    actual var headPosition: Int
        get() = _headPosition.value
        set(value) {
            _headPosition.value = value
        }

    actual var headEndExclusive: Int
        get() = _headEndExclusive.value
        set(value) {
            _headEndExclusive.value = value
        }
}
