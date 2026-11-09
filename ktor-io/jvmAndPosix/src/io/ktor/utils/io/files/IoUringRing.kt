/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.utils.io.files

import io.ktor.utils.io.InternalAPI
import io.ktor.utils.io.locks.SynchronizedObject
import io.ktor.utils.io.locks.synchronized
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@OptIn(InternalAPI::class)
internal class IoUringRing(
    internal val driver: IoUringDriver,
    private val handle: Long,
    entryCount: Int,
    private val pollerThread: Boolean = true,
    internal val eventFd: Int = -1,
) {
    private val lock = SynchronizedObject()
    private val slots = arrayOfNulls<PendingRequest>(entryCount)
    private val completionSlots = IntArray(entryCount)
    private val completionResults = IntArray(entryCount)
    private val readBufferPool = ArrayDeque<Long>()
    private var stopped = false
    private var closed = false

    init {
        if (pollerThread) {
            try {
                startPollerThread("ktor-io-uring-$handle", ::pollCompletions)
            } catch (cause: Throwable) {
                driver.destroyRing(handle)
                throw cause
            }
        }
    }

    suspend fun submitOpen(
        path: String,
        onSlot: (Int) -> Unit,
        onCompletion: (Int, Boolean) -> Unit,
    ): Int = submit(onSlot, onCompletion) { slot ->
        driver.submitOpen(handle, path, slot)
    }

    suspend fun submitRead(
        fd: Int,
        address: Long,
        length: Int,
        offset: Long,
        onSlot: (Int) -> Unit,
        onCompletion: (Int, Boolean) -> Unit,
    ): Int = submit(onSlot, onCompletion) { slot ->
        driver.submitRead(handle, fd, address, length, offset, slot)
    }

    suspend fun submitClose(
        fd: Int,
        onSlot: (Int) -> Unit,
        onCompletion: (Int, Boolean) -> Unit,
    ): Int = submit(onSlot, onCompletion, cancellable = false) { slot ->
        driver.submitClose(handle, fd, slot)
    }

    fun submitCloseAsync(
        fd: Int,
        onSlot: (Int) -> Unit,
        onCompletion: (Int, Boolean) -> Unit,
    ) {
        startRequest(
            PendingRequest(
                continuation = null,
                onSlot = onSlot,
                onCompletion = onCompletion,
                cancellable = false,
            ),
        ) { slot -> driver.submitClose(handle, fd, slot) }
    }

    fun cancel(slot: Int, cause: CancellationException?) {
        val request = synchronized(lock) { slots.getOrNull(slot) } ?: return
        if (request.cancellable) request.continuation?.cancel(cause)
    }

    fun shutdown() {
        val shouldWake = synchronized(lock) {
            if (stopped) {
                false
            } else {
                stopped = true
                true
            }
        }
        if (shouldWake) driver.submitWake(handle)
    }

    fun processCompletions(): Boolean {
        if (synchronized(lock) { closed }) return false
        while (true) {
            val count = try {
                driver.drainCompletions(handle, completionSlots, completionResults)
            } catch (_: Throwable) {
                -5
            }
            if (count < 0) {
                failPending(count)
                return false
            }
            if (count == 0) return true
            if (processCompletions(count)) return false
        }
    }

    fun close() {
        val shouldClose = synchronized(lock) {
            if (closed) {
                false
            } else {
                closed = true
                stopped = true
                true
            }
        }
        if (!shouldClose) return

        failPending(-125)
        val buffers = synchronized(lock) { readBufferPool.toList().also { readBufferPool.clear() } }
        buffers.forEach(driver::free)
        driver.destroyRing(handle)
    }

    fun acquireReadBuffer(size: Int): Long {
        val pooled = synchronized(lock) {
            check(!closed) { "The io_uring ring has been closed" }
            readBufferPool.removeFirstOrNull()
        }
        return pooled ?: driver.allocate(size)
    }

    fun releaseReadBuffer(address: Long) {
        val shouldFree = synchronized(lock) {
            if (closed || readBufferPool.size >= MAX_IDLE_READ_BUFFERS) {
                true
            } else {
                readBufferPool.addLast(address)
                false
            }
        }
        if (shouldFree) driver.free(address)
    }

    private suspend fun submit(
        onSlot: (Int) -> Unit,
        onCompletion: (Int, Boolean) -> Unit,
        cancellable: Boolean = true,
        operation: (Int) -> Int,
    ): Int = suspendCancellableCoroutine { continuation ->
        val request = PendingRequest(continuation, onSlot, onCompletion, cancellable)
        if (cancellable) continuation.invokeOnCancellation { cancelRequest(request) }
        startRequest(request, operation)
    }

    private fun startRequest(request: PendingRequest, operation: (Int) -> Int) {
        val slot = synchronized(lock) {
            if (stopped || closed) {
                -125
            } else {
                val available = slots.indexOfFirst { it == null }
                if (available >= 0) {
                    request.slot = available
                    slots[available] = request
                }
                if (available < 0) -11 else available
            }
        }

        if (slot < 0) {
            finishRequest(request, slot)
            return
        }

        request.onSlot(slot)

        val submissionResult = synchronized(lock) {
            if (request.cancelled) {
                slots[slot] = null
                null
            } else {
                val result = operation(slot)
                if (result < 0) {
                    slots[slot] = null
                } else {
                    request.submitted = true
                }
                result
            }
        }

        when {
            submissionResult == null -> finishRequest(request, -125)
            submissionResult < 0 -> finishRequest(request, submissionResult)
        }
    }

    private fun cancelRequest(request: PendingRequest) {
        synchronized(lock) {
            if (request.completed || request.cancelled) return
            request.cancelled = true
            if (request.submitted && request.cancellable) {
                driver.submitCancel(handle, request.slot)
            }
        }
    }

    private fun pollCompletions() {
        try {
            while (true) {
                val count = driver.waitCompletions(handle, completionSlots, completionResults)
                if (count == -4) continue
                if (count < 0) {
                    failPending(count)
                    return
                }
                if (processCompletions(count)) return
            }
        } catch (_: Throwable) {
            failPending(-5)
        } finally {
            close()
        }
    }

    private fun processCompletions(count: Int): Boolean {
        for (index in 0 until count.coerceAtMost(completionSlots.size)) {
            when (val slot = completionSlots[index]) {
                CANCEL_SLOT -> Unit
                WAKE_SLOT -> return true
                else -> finishSlot(slot, completionResults[index])
            }
        }
        return false
    }

    private fun finishSlot(slot: Int, result: Int) {
        val request = synchronized(lock) {
            slots.getOrNull(slot)?.also { slots[slot] = null }
        } ?: return
        finishRequest(request, result)
    }

    private fun failPending(result: Int) {
        val pending = synchronized(lock) {
            slots.mapNotNull { request ->
                if (request == null) {
                    null
                } else {
                    request.completed = true
                    request
                }
            }.also { slots.fill(null) }
        }

        pending.forEach { request ->
            completeRequest(request, result, request.cancelled)
        }
    }

    private fun finishRequest(request: PendingRequest, result: Int) {
        val cancelled = synchronized(lock) {
            if (request.completed) return
            request.completed = true
            if (request.slot >= 0 && slots.getOrNull(request.slot) === request) {
                slots[request.slot] = null
            }
            request.cancelled
        }
        completeRequest(request, result, cancelled)
    }

    private fun completeRequest(request: PendingRequest, result: Int, cancelled: Boolean) {
        var completionFailure: Throwable? = null
        try {
            request.onCompletion(result, cancelled)
        } catch (cause: Throwable) {
            completionFailure = cause
        }

        val continuation = request.continuation ?: return
        if (cancelled) return
        completionFailure?.let(continuation::resumeWithException) ?: continuation.resume(result)
    }

    private class PendingRequest(
        val continuation: CancellableContinuation<Int>?,
        val onSlot: (Int) -> Unit,
        val onCompletion: (Int, Boolean) -> Unit,
        val cancellable: Boolean,
    ) {
        var slot: Int = -1
        var submitted: Boolean = false
        var cancelled: Boolean = false
        var completed: Boolean = false
    }

    private companion object {
        const val CANCEL_SLOT: Int = -1
        const val WAKE_SLOT: Int = -2
        const val MAX_IDLE_READ_BUFFERS: Int = 8
    }
}
