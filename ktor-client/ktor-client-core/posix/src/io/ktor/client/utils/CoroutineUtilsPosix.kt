/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.utils

import io.ktor.util.*
import kotlinx.coroutines.*
import kotlin.native.concurrent.*

/**
 * Creates [CoroutineDispatcher] for client with fixed [threadCount] and specified [dispatcherName].
 */
@InternalAPI
public actual fun Dispatchers.clientDispatcher(
    threadCount: Int,
    dispatcherName: String
): CoroutineDispatcher = Unconfined

internal actual fun checkCoroutinesVersion() {
    try {
        val parent = Job()
        parent.freeze()
        Job(parent)
    } catch (cause: Throwable) {
        val message = "Ktor native HttpClient requires kotlinx.coroutines version with `native-mt` suffix" +
            " (like `1.3.9-native-mt`). Consider checking the dependencies."

        throw Error(message)
    }
}
