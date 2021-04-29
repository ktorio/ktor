// ktlint-disable filename
/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.utils.io

import io.ktor.utils.io.core.*
import io.ktor.utils.io.core.internal.*
import kotlinx.atomicfu.*

internal actual class ByteChannelSequentialBaseSharedState actual constructor() {
    private val _closed: AtomicBoolean = atomic(false)
    private val _readByteOrder: AtomicRef<ByteOrder> = atomic(ByteOrder.BIG_ENDIAN)
    private val _writeByteOrder: AtomicRef<ByteOrder> = atomic(ByteOrder.BIG_ENDIAN)
    private val _totalBytesRead: AtomicLong = atomic(0L)
    private val _totalBytesWritten: AtomicLong = atomic(0L)
    private val _closedCause: AtomicRef<Throwable?> = atomic(null)
    private val _lastReadAvailable: AtomicInt = atomic(0)
    private val _lastReadView: AtomicRef<ChunkBuffer> = atomic(ChunkBuffer.Empty)

    actual var closed: Boolean
        get() = _closed.value
        set(value) {
            _closed.value = value
        }

    actual var readByteOrder: ByteOrder
        get() = _readByteOrder.value
        set(value) {
            _readByteOrder.value = value
        }

    actual var writeByteOrder: ByteOrder
        get() = _writeByteOrder.value
        set(value) {
            _writeByteOrder.value = value
        }

    actual var totalBytesRead: Long
        get() = _totalBytesRead.value
        set(value) {
            _totalBytesRead.value = value
        }

    actual var totalBytesWritten: Long
        get() = _totalBytesWritten.value
        set(value) {
            _totalBytesWritten.value = value
        }

    actual var closedCause: Throwable?
        get() = _closedCause.value
        set(value) {
            _closedCause.value = value
        }

    actual var lastReadAvailable: Int
        get() = _lastReadAvailable.value
        set(value) {
            _lastReadAvailable.value = value
        }

    actual var lastReadView: ChunkBuffer
        get() = _lastReadView.value
        set(value) {
            _lastReadView.value = value
        }
}
