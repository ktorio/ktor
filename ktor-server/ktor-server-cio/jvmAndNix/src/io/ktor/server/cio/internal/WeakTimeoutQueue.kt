/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.cio.internal

import io.ktor.util.date.*
import kotlinx.coroutines.*

public expect class WeakTimeoutQueue constructor(
    timeoutMillis: Long,
    clock: () -> Long = { getTimeMillis() }
) {
    public val timeoutMillis: Long

    public suspend fun <T> withTimeout(block: suspend CoroutineScope.() -> T): T

    public fun process()

    public fun cancel()
}
