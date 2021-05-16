/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.http.cio.internals

import io.ktor.util.*
import kotlinx.coroutines.*

@InternalAPI
@Suppress("KDocMissingDocumentation")
public actual class WeakTimeoutQueue actual constructor(
    public actual val timeoutMillis: Long,
    clock: () -> Long
) {

    public actual fun cancel() {
    }

    public actual fun process() {
    }

    internal actual fun count(): Int {
        return 0
    }

    public actual suspend fun <T> withTimeout(block: suspend CoroutineScope.() -> T): T {
        //TODO
        return withTimeout(timeoutMillis, block)
    }

}
