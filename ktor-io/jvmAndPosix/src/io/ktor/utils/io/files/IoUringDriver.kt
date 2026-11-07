/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.utils.io.files

import kotlinx.io.Buffer

internal interface IoUringDriver {
    fun createRing(entries: Int): Long
    fun destroyRing(ring: Long)
    fun submitOpen(ring: Long, path: String, slot: Int): Int
    fun submitRead(ring: Long, fd: Int, address: Long, length: Int, offset: Long, slot: Int): Int
    fun submitClose(ring: Long, fd: Int, slot: Int): Int
    fun submitCancel(ring: Long, slot: Int): Int
    fun submitWake(ring: Long): Int
    fun waitCompletions(ring: Long, slots: IntArray, results: IntArray): Int
    fun allocate(size: Int): Long
    fun free(address: Long)
    fun copyToBuffer(address: Long, length: Int, sink: Buffer)
}

internal expect fun ioUringDriverOrNull(): IoUringDriver?

internal expect fun ioUringAvailableProcessors(): Int

internal expect fun startPollerThread(name: String, block: () -> Unit)
