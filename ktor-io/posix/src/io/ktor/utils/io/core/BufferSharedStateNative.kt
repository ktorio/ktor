// ktlint-disable filename
/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.utils.io.core

import io.ktor.utils.io.concurrent.*
import kotlinx.atomicfu.*

internal actual class BufferSharedState actual constructor(limit: Int) {
    private val _readPosition: AtomicInt = atomic(0)
    private val _writePosition: AtomicInt = atomic(0)
    private val _startGap: AtomicInt = atomic(0)
    private val _limit: AtomicInt = atomic(limit)
    private val _attachment: AtomicRef<Any?> = atomic<Any?>(null)

    actual var readPosition: Int
        get() = _readPosition.value
        set(value) {
            _readPosition.value = value
        }

    actual var writePosition: Int
        get() = _writePosition.value
        set(value) {
            _writePosition.value = value
        }

    actual var startGap: Int
        get() = _startGap.value
        set(value) {
            _startGap.value = value
        }

    actual var limit: Int
        get() = _limit.value
        set(value) {
            _limit.value = value
        }

    actual var attachment: Any?
        get() = _attachment.value
        set(value) {
            _attachment.value = value
        }
}
