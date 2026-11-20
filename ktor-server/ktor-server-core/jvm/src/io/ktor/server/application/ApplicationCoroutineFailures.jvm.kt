/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.application

import kotlin.coroutines.CoroutineContext

// The same target as kotlinx.coroutines uses for a coroutine without a CoroutineExceptionHandler
internal actual fun handleUnhandledCoroutineFailure(
    application: Application,
    context: CoroutineContext,
    exception: Throwable,
) {
    val thread = Thread.currentThread()
    thread.uncaughtExceptionHandler.uncaughtException(thread, exception)
}
