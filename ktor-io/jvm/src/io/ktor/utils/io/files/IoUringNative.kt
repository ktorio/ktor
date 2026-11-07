/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.utils.io.files

import java.nio.ByteBuffer

internal object IoUringNative {
    init {
        check(NativeLibraryLoader.load()) { "The io_uring JNI library is unavailable" }
    }

    external fun ringCreate(entries: Int): Long
    external fun ringDestroy(ring: Long)
    external fun submitOpen(ring: Long, path: String, slot: Int): Int
    external fun submitRead(ring: Long, fd: Int, address: Long, length: Int, offset: Long, slot: Int): Int
    external fun submitClose(ring: Long, fd: Int, slot: Int): Int
    external fun submitCancel(ring: Long, slot: Int): Int
    external fun submitWake(ring: Long): Int
    external fun waitCompletions(ring: Long, slots: IntArray, results: IntArray): Int
    external fun allocateBuffer(size: Int): ByteBuffer?
    external fun bufferAddress(buffer: ByteBuffer): Long
    external fun freeBuffer(buffer: ByteBuffer)
}
