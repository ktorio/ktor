/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.sockets.tests

import java.util.*

actual fun isJvmWindows(): Boolean {
    val os = System.getProperty("os.name", "unknown").lowercase(Locale.getDefault())
    return os.contains("win")
}
