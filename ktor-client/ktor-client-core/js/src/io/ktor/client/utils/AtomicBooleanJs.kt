/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.utils

import kotlinx.atomicfu.*
import kotlinx.atomicfu.AtomicBoolean

internal actual class AtomicBoolean actual constructor(value: Boolean) {

    private val _value = atomic(value)

    actual val value: Boolean
        get() = _value.value

    actual fun compareAndSet(expect: Boolean, update: Boolean): Boolean {
        return _value.compareAndSet(expect, update)
    }
}
