/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.utils.io

import io.ktor.utils.io.locks.*
import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlinx.io.Buffer
import kotlinx.io.Sink
import kotlinx.io.Source
import kotlin.concurrent.Volatile
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlin.coroutines.intrinsics.intercepted
import kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn
import kotlin.jvm.JvmStatic

internal expect val DEVELOPMENT_MODE: Boolean
internal const val CHANNEL_MAX_SIZE: Int = 1024 * 1024

/**
 * Sequential (non-concurrent) byte channel implementation
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.utils.io.ByteChannel)
 */
public class ByteChannel(public override val autoFlush: Boolean = false) : ByteReadChannel, BufferedByteWriteChannel {
    private val flushBuffer: Buffer = Buffer()

    @Volatile
    private var flushBufferSize = 0

    @OptIn(InternalAPI::class)
    private val flushBufferMutex = SynchronizedObject()

    // Reusable suspension caches - avoids allocation per suspension
    private val readSuspension = ReusableSuspension()
    private val writeSuspension = ReusableSuspension()

    // Tracks which suspension is currently active
    private val slotState: AtomicRef<SlotState> = atomic(SlotState.Empty)

    private val _readBuffer = Buffer()
    private val _writeBuffer = Buffer()
    private val _closedCause = atomic<CloseToken?>(null)

    @InternalAPI
    override val readBuffer: Source
        get() {
            _closedCause.value?.throwOrNull(::ClosedReadChannelException)
            if (_readBuffer.exhausted()) moveFlushToReadBuffer()
            return _readBuffer
        }

    @InternalAPI
    override val writeBuffer: Sink
        get() {
            if (isClosedForWrite) {
                _closedCause.value?.throwOrNull(::ClosedWriteChannelException)
                    ?: throw ClosedWriteChannelException()
            }
            return _writeBuffer
        }

    override val closedCause: Throwable?
        get() = _closedCause.value?.wrapCause()

    override val isClosedForWrite: Boolean
        get() = _closedCause.value != null

    override val isClosedForRead: Boolean
        get() = (closedCause != null) || (isClosedForWrite && flushBufferSize == 0 && _readBuffer.exhausted())

    @OptIn(InternalAPI::class)
    override suspend fun awaitContent(min: Int): Boolean {
        rethrowCloseCauseIfNeeded()
        if (_readBuffer.size >= min) return true

        sleepForRead {
            flushBufferSize + _readBuffer.size < min && _closedCause.value == null
        }

        if (_readBuffer.size < CHANNEL_MAX_SIZE) moveFlushToReadBuffer()
        return _readBuffer.size >= min
    }

    @OptIn(InternalAPI::class)
    private fun moveFlushToReadBuffer() {
        synchronized(flushBufferMutex) {
            flushBuffer.transferTo(_readBuffer)
            flushBufferSize = 0
        }

        resumeWriteSlot()
    }

    @OptIn(InternalAPI::class)
    override suspend fun flush() {
        rethrowCloseCauseIfNeeded()

        flushWriteBuffer()
        if (flushBufferSize < CHANNEL_MAX_SIZE) return

        sleepForWrite {
            flushBufferSize >= CHANNEL_MAX_SIZE && _closedCause.value == null
        }
    }

    @InternalAPI
    public override fun flushWriteBuffer() {
        if (_writeBuffer.exhausted()) return

        synchronized(flushBufferMutex) {
            val count = _writeBuffer.size.toInt()
            flushBuffer.transferFrom(_writeBuffer)
            flushBufferSize += count
        }

        resumeReadSlot()
    }

    @OptIn(InternalAPI::class)
    override fun close() {
        flushWriteBuffer()

        // It's important to flush before we have closedCause set
        if (!_closedCause.compareAndSet(null, CLOSED)) return
        closeSlot(null)
    }

    override suspend fun flushAndClose() {
        runCatching {
            flush()
        }

        // It's important to flush before we have closedCause set
        if (!_closedCause.compareAndSet(null, CLOSED)) return
        closeSlot(null)
    }

    override fun cancel(cause: Throwable?) {
        if (_closedCause.value != null) return

        val closedToken = CloseToken(cause)
        _closedCause.compareAndSet(null, closedToken)
        val wrappedCause = closedToken.wrapCause()

        closeSlot(wrappedCause)
    }

    override fun toString(): String = "ByteChannel[${hashCode()}]"

    /**
     * Suspends while the condition is true, using reusable read suspension.
     * Avoids allocation per suspension by reusing the same ReusableSuspension object.
     */
    private suspend inline fun sleepForRead(crossinline shouldSleep: () -> Boolean) {
        while (shouldSleep()) {
            suspendCoroutineUninterceptedOrReturn { ucont ->
                trySuspendForRead(ucont.intercepted(), shouldSleep)
            }
        }
    }

    /**
     * Suspends while the condition is true, using reusable write suspension.
     * Avoids allocation per suspension by reusing the same ReusableSuspension object.
     */
    private suspend inline fun sleepForWrite(crossinline shouldSleep: () -> Boolean) {
        while (shouldSleep()) {
            suspendCoroutineUninterceptedOrReturn { ucont ->
                trySuspendForWrite(ucont.intercepted(), shouldSleep)
            }
        }
    }

    private inline fun trySuspendForRead(
        continuation: Continuation<Unit>,
        crossinline shouldSleep: () -> Boolean
    ): Any {
        // Try to set state to ReadWaiting
        val previous = slotState.value
        if (previous is SlotState.Closed) {
            previous.cause?.let { throw it }
            return Unit
        }

        if (!slotState.compareAndSet(previous, SlotState.ReadWaiting)) {
            return Unit // CAS failed, retry in loop
        }

        // Handle previous waiter
        when (previous) {
            SlotState.ReadWaiting -> {
                // Only throw ConcurrentIOException if there's actually a waiter.
                // After cancellation, the suspension may have been resumed but slot state not yet cleared.
                if (readSuspension.isWaiting()) {
                    readSuspension.resumeWithException(ConcurrentIOException("read", null))
                }
            }
            SlotState.WriteWaiting -> {
                writeSuspension.resume()
            }
            else -> {}
        }

        // Check if we still need to sleep
        if (!shouldSleep()) {
            resumeReadSlot()
            return Unit
        }

        val result = readSuspension.trySuspend(continuation)
        // If trySuspend returned early (not suspended), reset slot state
        if (result !== COROUTINE_SUSPENDED) {
            slotState.compareAndSet(SlotState.ReadWaiting, SlotState.Empty)
        }
        return result
    }

    private inline fun trySuspendForWrite(
        continuation: Continuation<Unit>,
        crossinline shouldSleep: () -> Boolean
    ): Any {
        // Try to set state to WriteWaiting
        val previous = slotState.value
        if (previous is SlotState.Closed) {
            previous.cause?.let { throw it }
            return Unit
        }

        if (!slotState.compareAndSet(previous, SlotState.WriteWaiting)) {
            return Unit // CAS failed, retry in loop
        }

        // Handle previous waiter
        when (previous) {
            SlotState.WriteWaiting -> {
                // Only throw ConcurrentIOException if there's actually a waiter.
                // After cancellation, the suspension may have been resumed but slot state not yet cleared.
                if (writeSuspension.isWaiting()) {
                    writeSuspension.resumeWithException(ConcurrentIOException("write", null))
                }
            }
            SlotState.ReadWaiting -> {
                readSuspension.resume()
            }
            else -> {}
        }

        // Check if we still need to sleep
        if (!shouldSleep()) {
            resumeWriteSlot()
            return Unit
        }

        val result = writeSuspension.trySuspend(continuation)
        // If trySuspend returned early (not suspended), reset slot state
        if (result !== COROUTINE_SUSPENDED) {
            slotState.compareAndSet(SlotState.WriteWaiting, SlotState.Empty)
        }
        return result
    }

    /**
     * Resumes the read waiter if one is waiting.
     * Called after flushing write buffer to unblock readers.
     */
    private fun resumeReadSlot() {
        if (slotState.compareAndSet(SlotState.ReadWaiting, SlotState.Empty)) {
            readSuspension.resume()
        }
    }

    /**
     * Resumes the write waiter if one is waiting.
     * Called after reading from buffer to unblock writers.
     */
    private fun resumeWriteSlot() {
        if (slotState.compareAndSet(SlotState.WriteWaiting, SlotState.Empty)) {
            writeSuspension.resume()
        }
    }

    /**
     * Closes both suspension slots with the given cause.
     */
    private fun closeSlot(cause: Throwable?) {
        val closedState = if (cause != null) SlotState.Closed(cause) else SlotState.CLOSED
        val previous = slotState.getAndSet(closedState)

        when (previous) {
            SlotState.ReadWaiting -> readSuspension.close(cause)
            SlotState.WriteWaiting -> writeSuspension.close(cause)
            else -> {}
        }
    }

    /**
     * Tracks the current suspension state without allocating per suspension.
     */
    private sealed interface SlotState {
        data object Empty : SlotState
        data object ReadWaiting : SlotState
        data object WriteWaiting : SlotState
        data class Closed(val cause: Throwable?) : SlotState

        companion object {
            @JvmStatic
            val CLOSED = Closed(null)
        }
    }
}

/**
 * Thrown when a coroutine awaiting I/O is replaced by another.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.utils.io.ConcurrentIOException)
 */
public class ConcurrentIOException(
    taskName: String,
    cause: Throwable? = null
) : IllegalStateException("Concurrent $taskName attempts", cause)
