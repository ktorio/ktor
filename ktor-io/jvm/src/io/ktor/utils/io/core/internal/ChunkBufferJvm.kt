/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.utils.io.core.internal

import kotlinx.io.*
import kotlinx.io.unsafe.*
import java.nio.*

@Suppress("DEPRECATION")
@OptIn(UnsafeIoApi::class, InternalIoApi::class)
public fun ChunkBuffer.writeDirect(min: Int, block: (ByteBuffer) -> Unit) {
    UnsafeBufferOperations.writeToTail(buffer, min) { array, startIndex, endIndex ->
        val buffer = ByteBuffer.wrap(array, startIndex, endIndex - startIndex)
        block(buffer)
        return@writeToTail buffer.position() - startIndex
    }
}

@Suppress("DEPRECATION")
@OptIn(UnsafeIoApi::class, InternalIoApi::class)
public fun ChunkBuffer.readDirect(block: (ByteBuffer) -> Unit) {
    UnsafeBufferOperations.readFromHead(buffer) { array, start, endExclusive ->
        val wrap = ByteBuffer.wrap(array, start, endExclusive - start)
        block(wrap)

        val consumed = wrap.position() - start
        consumed
    }
}
