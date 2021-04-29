/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.utils.io.core

import io.ktor.utils.io.bits.*
import io.ktor.utils.io.core.internal.*
import kotlinx.atomicfu.*

internal actual class AbstractOutputSharedState {
    private val _head: AtomicRef<ChunkBuffer?> = atomic(null)
    private val _tail: AtomicRef<ChunkBuffer?> = atomic(null)
    private val _tailMemory: AtomicRef<Memory> = atomic(Memory.Empty)
    private val _tailPosition: AtomicInt = atomic(0)
    private val _tailEndExclusive: AtomicInt = atomic(0)
    private val _tailInitialPosition: AtomicInt = atomic(0)
    private val _chainedSize: AtomicInt = atomic(0)

    actual var head: ChunkBuffer?
        get() = _head.value
        set(value) {
            _head.value = value
        }

    actual var tail: ChunkBuffer?
        get() = _tail.value
        set(value) {
            _tail.value = value
        }

    actual var tailMemory: Memory
        get() = _tailMemory.value
        set(value) {
            _tailMemory.value = value
        }

    actual var tailPosition: Int
        get() = _tailPosition.value
        set(value) {
            _tailPosition.value = value
        }

    actual var tailEndExclusive: Int
        get() = _tailEndExclusive.value
        set(value) {
            _tailEndExclusive.value = value
        }

    actual var tailInitialPosition: Int
        get() = _tailInitialPosition.value
        set(value) {
            _tailInitialPosition.value = value
        }

    actual var chainedSize: Int
        get() = _chainedSize.value
        set(value) {
            _chainedSize.value = value
        }
}
