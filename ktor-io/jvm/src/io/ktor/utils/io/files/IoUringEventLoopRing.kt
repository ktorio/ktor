/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.utils.io.files

import io.ktor.utils.io.InternalAPI

/**
 * Access to an io_uring ring registered with the current event loop.
 */
@InternalAPI
public object IoUringEventLoopRing {
    private const val RING_ENTRIES = 256
    private val current = ThreadLocal<IoUringRing?>()

    /**
     * Whether io_uring is available for the current JVM and operating system.
     */
    public val isAvailable: Boolean
        get() = IoUringWorkerPool.isAvailable

    /**
     * The eventfd for the ring bound to the current thread, or `-1` if no ring is bound.
     */
    public val eventFd: Int
        get() = current.get()?.eventFd ?: -1

    /**
     * Creates and binds an event-loop ring to the current thread.
     *
     * @return the ring eventfd, or `-1` when eventfd integration is unavailable.
     */
    public fun createForCurrentThread(): Int {
        current.get()?.let { return it.eventFd }
        val driver = ioUringDriverOrNull() ?: return -1
        var handle = 0L
        return try {
            handle = driver.createRing(RING_ENTRIES)
            val eventFd = driver.registerEventFd(handle)
            if (eventFd < 0) {
                driver.destroyRing(handle)
                return -1
            }
            current.set(IoUringRing(driver, handle, RING_ENTRIES, pollerThread = false, eventFd = eventFd))
            eventFd
        } catch (_: Throwable) {
            if (handle != 0L) driver.destroyRing(handle)
            -1
        }
    }

    /**
     * Drains all available completions for the current thread's ring.
     *
     * @return `true` when the ring remains active, or `false` when it is closed or failed.
     */
    public fun processCompletions(): Boolean {
        return current.get()?.processCompletions() == true
    }

    /**
     * Closes the ring bound to the current thread.
     */
    public fun close() {
        val ring = current.get()
        current.remove()
        ring?.close()
    }

    internal val currentRing: IoUringRing?
        get() = current.get()
}
