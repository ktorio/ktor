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
    private val freeSlots = IntArray(entryCount) { it }
    private val completionSlots = IntArray(entryCount)
    private val completionResults = IntArray(entryCount)
    private val readBufferPool = ArrayDeque<Long>()
    private val pendingRequests = ArrayDeque<PendingRequest>()
    private var freeSlotCount = entryCount
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
        owner: Any? = null,
    ): Int = submit(onSlot, onCompletion, owner = owner) { slot ->
        driver.submitOpen(handle, path, slot)
    }

    suspend fun submitRead(
        fd: Int,
        address: Long,
        length: Int,
        offset: Long,
        onSlot: (Int) -> Unit,
        onCompletion: (Int, Boolean) -> Unit,
        owner: Any? = null,
    ): Int = submit(onSlot, onCompletion, owner = owner) { slot ->
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
                owner = null,
                operation = { slot -> driver.submitClose(handle, fd, slot) },
            )
        )
    }

    fun cancel(slot: Int, cause: CancellationException?) {
        val request = synchronized(lock) { slots.getOrNull(slot) } ?: return
        if (request.cancellable) request.continuation?.cancel(cause)
    }

    fun cancel(owner: Any, cause: CancellationException?) {
        val request = synchronized(lock) {
            pendingRequests.firstOrNull { it.owner === owner }
                ?: slots.firstOrNull { it?.owner === owner }
        } ?: return
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
        owner: Any? = null,
        operation: (Int) -> Int,
    ): Int = suspendCancellableCoroutine { continuation ->
        val request = PendingRequest(continuation, onSlot, onCompletion, cancellable, owner, operation)
        if (cancellable) continuation.invokeOnCancellation { cancelRequest(request) }
        startRequest(request)
    }

    private fun startRequest(request: PendingRequest) {
        val slot = synchronized(lock) {
            if (stopped || closed || request.cancelled) {
                STOPPED_SLOT
            } else if (freeSlotCount == 0 || pendingRequests.isNotEmpty()) {
                request.queued = true
                pendingRequests.addLast(request)
                QUEUED_SLOT
            } else {
                reserveSlotLocked(request)
            }
        }

        when (slot) {
            STOPPED_SLOT -> finishRequest(request, -125)

            QUEUED_SLOT -> return

            else -> {
                if (submitRequest(request, slot, prioritizePending = true)) startPendingRequests()
            }
        }
    }

    private fun submitRequest(request: PendingRequest, slot: Int, prioritizePending: Boolean): Boolean {
        request.onSlot(slot)

        val submissionResult = synchronized(lock) {
            if (request.cancelled || stopped || closed) {
                releaseSlotLocked(request, slot)
                null
            } else {
                val result = request.operation(slot)
                if (result >= 0) {
                    request.submitted = true
                } else if (result != EAGAIN) {
                    releaseSlotLocked(request, slot)
                }
                result
            }
        }

        when {
            submissionResult == null -> {
                finishRequest(request, -125, prioritizePending)
                return true
            }

            submissionResult == EAGAIN -> {
                request.onSlot(NO_SLOT)
                val queued = synchronized(lock) {
                    releaseSlotLocked(request, slot)
                    if (request.cancelled || stopped || closed) {
                        false
                    } else {
                        request.slot = NO_SLOT
                        request.submitted = false
                        request.queued = true
                        pendingRequests.addLast(request)
                        true
                    }
                }
                if (!queued) finishRequest(request, -125)
                return false
            }

            submissionResult < 0 -> finishRequest(request, submissionResult, prioritizePending)
        }
        return true
    }

    private fun startPendingRequests() {
        while (true) {
            val requestAndSlot = synchronized(lock) {
                if (stopped || closed || freeSlotCount == 0 || pendingRequests.isEmpty()) return

                val request = pendingRequests.removeFirst()
                request.queued = false
                if (request.cancelled) {
                    request to STOPPED_SLOT
                } else {
                    request to reserveSlotLocked(request)
                }
            }

            val (request, slot) = requestAndSlot
            if (slot == STOPPED_SLOT) {
                finishRequest(request, -125)
            } else if (!submitRequest(request, slot, prioritizePending = false)) {
                return
            }
        }
    }

    private fun reserveSlotLocked(request: PendingRequest): Int {
        val slot = freeSlots[--freeSlotCount]
        request.slot = slot
        slots[slot] = request
        return slot
    }

    private fun releaseSlotLocked(request: PendingRequest, slot: Int) {
        if (slots.getOrNull(slot) === request) {
            slots[slot] = null
            freeSlots[freeSlotCount++] = slot
        }
    }

    private fun cancelRequest(request: PendingRequest) {
        var wasQueued = false
        synchronized(lock) {
            if (request.completed || request.cancelled) return
            request.cancelled = true
            if (request.queued) {
                pendingRequests.remove(request)
                request.queued = false
                wasQueued = true
            } else if (request.submitted && request.cancellable) {
                driver.submitCancel(handle, request.slot)
            }
        }
        if (wasQueued) {
            finishRequest(request, -125, prioritizePending = true)
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
            slots.getOrNull(slot)
        } ?: return
        finishRequest(request, result, prioritizePending = true)
    }

    private fun failPending(result: Int) {
        val pending = synchronized(lock) {
            stopped = true
            val requests = slots.mapNotNull { it } + pendingRequests.toList()
            requests.forEach {
                it.completed = true
                it.queued = false
            }
            slots.fill(null)
            pendingRequests.clear()
            freeSlotCount = freeSlots.size
            requests
        }

        pending.forEach { request ->
            completeRequest(request, result, request.cancelled)
        }
    }

    private fun finishRequest(request: PendingRequest, result: Int, prioritizePending: Boolean = false) {
        val cancelled = synchronized(lock) {
            if (request.completed) return
            request.completed = true
            request.queued = false
            if (request.slot >= 0) releaseSlotLocked(request, request.slot)
            request.cancelled
        }
        if (prioritizePending) startPendingRequests()
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
        val owner: Any?,
        val operation: (Int) -> Int,
    ) {
        var slot: Int = NO_SLOT
        var submitted: Boolean = false
        var cancelled: Boolean = false
        var completed: Boolean = false
        var queued: Boolean = false
    }

    private companion object {
        const val CANCEL_SLOT: Int = -1
        const val WAKE_SLOT: Int = -2
        const val NO_SLOT: Int = -1
        const val QUEUED_SLOT: Int = -126
        const val STOPPED_SLOT: Int = -125
        const val EAGAIN: Int = -11
        const val MAX_IDLE_READ_BUFFERS: Int = 8
    }
}
