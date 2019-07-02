/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:Suppress("KDocMissingDocumentation")

package io.ktor.util

import io.ktor.utils.io.core.*
import java.util.concurrent.locks.*
import java.util.concurrent.locks.Lock

@InternalAPI
actual class Lock : Closeable {
    private val lock = ReentrantLock()

    actual fun lock() {
        lock.lock()
    }

    actual fun unlock() {
        lock.unlock()
    }

    override fun close() {
    }
}
