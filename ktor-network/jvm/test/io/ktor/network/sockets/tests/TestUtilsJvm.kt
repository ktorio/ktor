/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.sockets.tests

import java.util.*

internal actual fun Throwable.isPosixException(): Boolean = false

actual fun initSocketsIfNeeded() {}

actual fun isJvmWindows(): Boolean {
    val os = System.getProperty("os.name", "unknown").lowercase(Locale.getDefault())
    return os.contains("win")
}
