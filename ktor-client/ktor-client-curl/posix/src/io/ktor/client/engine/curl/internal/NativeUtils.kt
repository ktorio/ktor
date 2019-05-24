/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.curl.internal

import kotlinx.cinterop.*
import platform.posix.*

internal fun ByteArray.copyToBuffer(buffer: CPointer<ByteVar>, size: ULong, position: Int = 0) {
    usePinned { pinned ->
        memcpy(buffer, pinned.addressOf(position), size.convert())
    }
}

internal inline fun <T : Any> T.asStablePointer(): COpaquePointer = StableRef.create(this).asCPointer()

internal inline fun <reified T : Any> COpaquePointer.fromCPointer(): T = asStableRef<T>().get()

