/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.utils.io.files

import io.ktor.io.interop.mutex.ktor_pthread_create_detached
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.StableRef
import kotlinx.cinterop.asStableRef
import kotlinx.cinterop.staticCFunction
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.Platform

@OptIn(ExperimentalForeignApi::class)
private fun pollerThreadEntry(reference: COpaquePointer?): COpaquePointer? {
    val block = reference!!.asStableRef<() -> Unit>()
    try {
        block.get().invoke()
    } finally {
        block.dispose()
    }
    return null
}

@OptIn(ExperimentalForeignApi::class)
@Suppress("UNUSED_PARAMETER")
internal actual fun startPollerThread(name: String, block: () -> Unit) {
    val blockReference = StableRef.create(block)
    val result = ktor_pthread_create_detached(staticCFunction(::pollerThreadEntry), blockReference.asCPointer())
    if (result != 0) {
        blockReference.dispose()
        error("Failed to start io_uring poller thread: errno=$result")
    }
}

@OptIn(ExperimentalNativeApi::class)
internal actual fun ioUringAvailableProcessors(): Int = Platform.getAvailableProcessors()
