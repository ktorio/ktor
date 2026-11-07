/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.utils.io.files

private val driverOrNull: IoUringDriver? by lazy {
    if (!ioUringEnabled() || !NativeLibraryLoader.load()) {
        null
    } else {
        runCatching {
            val probe = JniIoUringDriver.createRing(8)
            JniIoUringDriver.destroyRing(probe)
            JniIoUringDriver
        }.getOrNull()
    }
}

internal actual fun ioUringDriverOrNull(): IoUringDriver? =
    if (ioUringEnabled()) driverOrNull else null

internal actual fun ioUringAvailableProcessors(): Int = Runtime.getRuntime().availableProcessors()

internal actual fun startPollerThread(name: String, block: () -> Unit) {
    Thread(block, name).apply {
        isDaemon = true
        start()
    }
}

private fun ioUringEnabled(): Boolean = try {
    val configuredValue = System.getProperty("io.ktor.io.uring.enabled")
        ?: System.getenv("io.ktor.io.uring.enabled")
        ?: System.getenv("IO_KTOR_IO_URING_ENABLED")
    !configuredValue.equals("false", ignoreCase = true)
} catch (_: SecurityException) {
    false
}
