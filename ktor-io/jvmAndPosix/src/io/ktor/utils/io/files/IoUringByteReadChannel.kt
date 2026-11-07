/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.utils.io.files

import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.CLOSED
import io.ktor.utils.io.CloseToken
import io.ktor.utils.io.CloseToken.Companion.wrapCause
import io.ktor.utils.io.InternalAPI
import io.ktor.utils.io.locks.SynchronizedObject
import io.ktor.utils.io.locks.synchronized
import kotlinx.coroutines.CancellationException
import kotlinx.io.Buffer
import kotlinx.io.IOException
import kotlinx.io.Source

@OptIn(InternalAPI::class)
internal class IoUringByteReadChannel(
    private val path: kotlinx.io.files.Path,
    private val ring: IoUringRing,
) : ByteReadChannel {
    private val driver = ring.driver
    private val buffer = Buffer()
    private val stateLock = SynchronizedObject()
    private var bufferReleased = false
    private var nativeBufferAddress = 0L
    private var fileDescriptor = -1
    private var offset = 0L
    private var eofReached = false
    private var stopped = false
    private var closing = false
    private var activeSlot = -1
    private var activeOperation = Operation.NONE
    private var closeToken: CloseToken? = null

    override val closedCause: Throwable?
        get() = synchronized(stateLock) { closeToken?.wrapCause() }

    override val isClosedForRead: Boolean
        get() = synchronized(stateLock) { closeToken != null && buffer.exhausted() }

    @InternalAPI
    override val readBuffer: Source
        get() = buffer

    override suspend fun awaitContent(min: Int): Boolean {
        require(min >= 0) { "min should not be negative: $min" }
        rethrowCloseCauseIfNeeded()
        if (buffer.size >= min.toLong()) return true
        if (synchronized(stateLock) { stopped }) return false

        try {
            openFileIfNeeded()
            while (buffer.size < min.toLong()) {
                val state = synchronized(stateLock) { Triple(stopped, eofReached, fileDescriptor) }
                if (state.first) break
                if (state.second) {
                    closeFile()
                    break
                }

                val address = readBufferAddress()
                val result = ring.submitRead(
                    fd = state.third,
                    address = address,
                    length = READ_BUFFER_SIZE,
                    offset = synchronized(stateLock) { offset },
                    onSlot = { setActiveOperation(it, Operation.READ) },
                    onCompletion = ::readCompleted,
                )

                if (result < 0) throw ioException("read", result)
                if (result == 0) {
                    closeFile()
                    break
                }
            }
        } catch (cause: CancellationException) {
            cancel(cause)
            throw cause
        } catch (cause: Throwable) {
            fail(cause)
            throw cause
        }

        rethrowCloseCauseIfNeeded()
        return buffer.size >= min.toLong()
    }

    override fun cancel(cause: Throwable?) {
        var slot = -1
        var operation = Operation.NONE
        var closeDescriptor = -1

        synchronized(stateLock) {
            if (closeToken != null) return
            stopped = true
            closeToken = CloseToken(cause)
            slot = activeSlot
            operation = activeOperation

            if (operation != Operation.READ) releaseBuffer()
            if (operation == Operation.NONE && fileDescriptor >= 0 && !closing) {
                closing = true
                closeDescriptor = fileDescriptor
            }
        }

        if (slot >= 0 && operation != Operation.CLOSE) {
            ring.cancel(slot, cause.asCancellationException())
        }
        if (closeDescriptor >= 0) closeFileAsync(closeDescriptor)
    }

    private suspend fun openFileIfNeeded() {
        val state = synchronized(stateLock) { stopped to fileDescriptor }
        if (state.first || state.second >= 0) return

        val result = ring.submitOpen(
            path = path.toString(),
            onSlot = { setActiveOperation(it, Operation.OPEN) },
            onCompletion = ::openCompleted,
        )
        if (result < 0) throw ioException("open", result)
    }

    private fun openCompleted(result: Int, cancelled: Boolean) {
        var closeDescriptor = -1
        synchronized(stateLock) {
            clearActiveOperation(Operation.OPEN)
            if (result >= 0) {
                if (stopped || cancelled) {
                    stopped = true
                    if (closeToken == null) closeToken = CloseToken(CancellationException("Channel was cancelled"))
                    closing = true
                    closeDescriptor = result
                } else {
                    fileDescriptor = result
                }
            }
            if (stopped || cancelled) releaseBuffer()
        }
        if (closeDescriptor >= 0) closeFileAsync(closeDescriptor)
    }

    private fun readCompleted(result: Int, cancelled: Boolean) {
        var closeDescriptor = -1
        synchronized(stateLock) {
            clearActiveOperation(Operation.READ)
            if (stopped || cancelled) {
                stopped = true
                if (closeToken == null) closeToken = CloseToken(CancellationException("Channel was cancelled"))
                releaseBuffer()
                if (fileDescriptor >= 0 && !closing) {
                    closing = true
                    closeDescriptor = fileDescriptor
                }
            } else if (result > 0) {
                driver.copyToBuffer(nativeBufferAddress, result, buffer)
                offset += result
            } else if (result == 0) {
                eofReached = true
            }
        }
        if (closeDescriptor >= 0) closeFileAsync(closeDescriptor)
    }

    private suspend fun closeFile() {
        val descriptor = synchronized(stateLock) {
            if (fileDescriptor < 0 || closing) return
            closing = true
            fileDescriptor
        }
        val result = ring.submitClose(
            fd = descriptor,
            onSlot = { setActiveOperation(it, Operation.CLOSE) },
            onCompletion = { result, _ -> closeCompleted(descriptor, result) },
        )
        if (result < 0) throw ioException("close", result)
    }

    private fun closeFileAsync(descriptor: Int) {
        ring.submitCloseAsync(
            fd = descriptor,
            onSlot = { setActiveOperation(it, Operation.CLOSE) },
            onCompletion = { result, _ -> closeCompleted(descriptor, result) },
        )
    }

    private fun closeCompleted(descriptor: Int, result: Int) {
        synchronized(stateLock) {
            clearActiveOperation(Operation.CLOSE)
            if (fileDescriptor == descriptor) fileDescriptor = -1
            closing = false
            releaseBuffer()
            if (result < 0) {
                if (closeToken == null || closeToken === CLOSED) {
                    closeToken = CloseToken(ioException("close", result))
                }
            } else if (!stopped && closeToken == null) {
                closeToken = CLOSED
            }
        }
    }

    private fun setActiveOperation(slot: Int, operation: Operation) {
        val cancel = synchronized(stateLock) {
            activeSlot = slot
            activeOperation = operation
            stopped && operation != Operation.CLOSE
        }
        if (cancel) ring.cancel(slot, CancellationException("Channel was cancelled"))
    }

    private fun clearActiveOperation(operation: Operation) {
        if (activeOperation == operation) {
            activeSlot = -1
            activeOperation = Operation.NONE
        }
    }

    private fun fail(cause: Throwable) {
        var closeDescriptor = -1
        synchronized(stateLock) {
            if (closeToken == null) closeToken = CloseToken(cause)
            stopped = true
            if (activeOperation != Operation.READ) releaseBuffer()
            if (activeOperation == Operation.NONE && fileDescriptor >= 0 && !closing) {
                closing = true
                closeDescriptor = fileDescriptor
            }
        }
        if (closeDescriptor >= 0) closeFileAsync(closeDescriptor)
    }

    private fun releaseBuffer() {
        if (bufferReleased) return
        bufferReleased = true
        if (nativeBufferAddress != 0L) driver.free(nativeBufferAddress)
    }

    private fun readBufferAddress(): Long = synchronized(stateLock) {
        check(!bufferReleased) { "The io_uring read buffer has already been released" }
        if (nativeBufferAddress == 0L) nativeBufferAddress = driver.allocate(READ_BUFFER_SIZE)
        nativeBufferAddress
    }

    private fun rethrowCloseCauseIfNeeded() {
        closedCause?.let { throw it }
    }

    private fun ioException(operation: String, result: Int): IOException =
        IOException("io_uring $operation failed: errno=${-result} path=$path")

    private fun Throwable?.asCancellationException(): CancellationException =
        this as? CancellationException
            ?: CancellationException(this?.message ?: "Channel was cancelled", this)

    private enum class Operation {
        NONE,
        OPEN,
        READ,
        CLOSE,
    }

    private companion object {
        const val READ_BUFFER_SIZE = 64 * 1024
    }
}
