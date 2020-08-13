/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:Suppress("KDocMissingDocumentation")

package io.ktor.util

import java.util.concurrent.locks.*

@InternalAPI
actual class Lock {
    private val lock = ReentrantLock()

    actual fun lock() {
        lock.lock()
    }

    actual fun unlock() {
        lock.unlock()
    }

    actual fun close() {
    }
}
