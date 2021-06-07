/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.util.cio

import kotlinx.coroutines.sync.Semaphore

@Deprecated(
    "Ktor Semaphore is deprecated and will be removed in ktor 2.0.0. " +
        "Consider using kotlinx.coroutines Semaphore instead.",
    level = DeprecationLevel.WARNING,
    replaceWith = ReplaceWith("Semaphore", "kotlinx.coroutines.sync.Semaphore")
)
public class Semaphore(public val limit: Int) {
    private val delegate = Semaphore(limit)

    @Deprecated(
        "Ktor Semaphore is deprecated and will be removed in ktor 2.0.0. " +
            "Consider using kotlinx.coroutines Semaphore instead.",
        level = DeprecationLevel.WARNING,
        replaceWith = ReplaceWith("acquire()", "kotlinx.coroutines.sync.Semaphore")
    )
    public suspend fun enter() {
        delegate.acquire()
    }

    public suspend fun acquire() {
        delegate.acquire()
    }

    @Deprecated(
        "Ktor Semaphore is deprecated and will be removed in ktor 2.0.0. " +
            "Consider using kotlinx.coroutines Semaphore instead.",
        level = DeprecationLevel.WARNING,
        replaceWith = ReplaceWith("release()", "kotlinx.coroutines.sync.Semaphore")
    )
    public fun leave() {
        delegate.release()
    }

    public fun release() {
        delegate.release()
    }
}
