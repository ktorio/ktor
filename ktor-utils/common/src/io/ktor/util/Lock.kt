/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

@file:Suppress("KDocMissingDocumentation")

package io.ktor.util

@InternalAPI
public expect class Lock() {
    public fun lock()
    public fun unlock()

    public fun close()
}

@InternalAPI
public inline fun <R> Lock.withLock(crossinline block: () -> R): R {
    try {
        lock()
        return block()
    } finally {
        unlock()
    }
}
