/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.engine

import io.ktor.application.*
import io.ktor.util.*
import kotlinx.coroutines.*
import kotlin.system.exitProcess as exitProcessJvm

internal actual fun availableProcessors(): Int {
    return Runtime.getRuntime().availableProcessors()
}

internal actual fun exitProcess(status: Int): Nothing {
    exitProcessJvm(status)
}

internal actual val defaultWatchPaths: List<String>
    get() = listOf(WORKING_DIRECTORY_PATH)

internal actual val ioDispatcher: CoroutineDispatcher
    get() = Dispatchers.IO

internal actual inline fun ApplicationEnvironment.catchOOM(
    cause: Throwable,
    block: () -> Unit
) {
    try {
        block()
    } catch (oom: OutOfMemoryError) {
        try {
            log.error(cause)
        } catch (oomAttempt2: OutOfMemoryError) {
            System.err.print("OutOfMemoryError: ")
            System.err.println(cause.message)
        }
    }
}
