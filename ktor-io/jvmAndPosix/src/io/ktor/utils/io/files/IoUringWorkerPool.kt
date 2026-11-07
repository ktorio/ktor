/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.utils.io.files

import kotlinx.atomicfu.atomic

internal object IoUringWorkerPool {
    private const val RING_ENTRIES = 256
    private const val MAX_WORKERS = 4

    private val nextWorker = atomic(0)
    private val rings: List<IoUringRing>? by lazy(::createRings)

    val isAvailable: Boolean
        get() = rings != null

    fun next(): IoUringRing {
        val workers = checkNotNull(rings) { "io_uring is not available" }
        val index = (nextWorker.getAndIncrement() and Int.MAX_VALUE) % workers.size
        return workers[index]
    }

    private fun createRings(): List<IoUringRing>? {
        val driver = ioUringDriverOrNull() ?: return null
        val workerCount = ioUringAvailableProcessors().coerceAtLeast(1).coerceAtMost(MAX_WORKERS)
        val created = ArrayList<IoUringRing>(workerCount)

        return try {
            repeat(workerCount) {
                val handle = driver.createRing(RING_ENTRIES)
                created += IoUringRing(driver, handle, RING_ENTRIES)
            }
            created
        } catch (_: Throwable) {
            created.forEach(IoUringRing::shutdown)
            null
        }
    }
}
