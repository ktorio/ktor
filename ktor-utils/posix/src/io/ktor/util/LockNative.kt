// ktlint-disable filename
/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util

import kotlinx.atomicfu.locks.*
import kotlin.native.concurrent.*

public actual class Lock {
    private val mutex = SynchronizedObject()

    public actual fun lock() {
        mutex.lock()
    }

    public actual fun unlock() {
        mutex.unlock()
    }

    public actual fun close() {
    }
}
