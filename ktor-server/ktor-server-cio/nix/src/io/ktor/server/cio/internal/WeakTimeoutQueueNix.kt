// ktlint-disable filename
/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.cio.internal

import kotlinx.coroutines.*

public actual class WeakTimeoutQueue actual constructor(
    public actual val timeoutMillis: Long,
    clock: () -> Long
) {
    private val context = Job()

    public actual suspend fun <T> withTimeout(block: suspend CoroutineScope.() -> T): T = withContext(context) {
        withTimeout(timeoutMillis) {
            block()
        }
    }

    public actual fun process() {
    }

    public actual fun cancel() {
        context.cancel()
    }
}
