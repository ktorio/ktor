/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util

import io.ktor.utils.io.core.*

@InternalAPI
actual class Lock : Closeable {
    actual fun lock() {}
    actual fun unlock() {}
    override fun close() {}
}
