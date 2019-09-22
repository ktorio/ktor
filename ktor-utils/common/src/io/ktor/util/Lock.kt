/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:Suppress("KDocMissingDocumentation")

package io.ktor.util

import io.ktor.utils.io.core.*

@InternalAPI
expect class Lock() : Closeable {
    fun lock()
    fun unlock()
}

@InternalAPI
inline fun <R> Lock.withLock(block: () -> R): R {
    try {
        lock()
        return block()
    } finally {
        unlock()
    }
}
