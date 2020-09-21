/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util

import kotlinx.cinterop.*
import utils.*
import kotlin.native.concurrent.*

@InternalAPI
public actual class Lock {
    private val mutex = nativeHeap.alloc<ktor_mutex_t>()

    init {
        freeze()
        ktor_mutex_create(mutex.ptr).checkResult { "Failed to create mutex." }
    }

    public actual fun lock() {
        ktor_mutex_lock(mutex.ptr).checkResult { "Failed to lock mutex." }
    }

    public actual fun unlock() {
        ktor_mutex_unlock(mutex.ptr).checkResult { "Failed to unlock mutex." }
    }

    public actual fun close() {
        ktor_mutex_destroy(mutex.ptr)
        nativeHeap.free(mutex)
    }
}

private inline fun Int.checkResult(block: () -> String) {
    check(this == 0, block)
}
