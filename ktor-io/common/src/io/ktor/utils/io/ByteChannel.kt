/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.utils.io

import io.ktor.utils.io.locks.*
import kotlinx.atomicfu.*
import kotlinx.io.*
import kotlin.concurrent.*

@InternalAPI
public val CHANNEL_MAX_SIZE: Int = 4096

/**
 * Sequential (non-concurrent) byte channel implementation
 */
public class ByteChannel(public val autoFlush: Boolean = false) : ByteReadChannel, BufferedByteWriteChannel {
    private val _closedCause = atomic<CloseToken?>(null)
    private val flushBuffer: Buffer = Buffer()

    @Volatile
    private var flushBufferSize = 0

    @OptIn(InternalAPI::class)
    private val mutex = SynchronizedObject()

    @OptIn(InternalAPI::class)
    private val readWriteLock = ReadWriteLock()

    private val _readBuffer = Buffer()
    private val _writeBuffer = Buffer()

    @InternalAPI
    override val readBuffer: Source
        get() {
            closedCause?.let { throw it }
            if (_readBuffer.exhausted()) moveFlushToReadBuffer()
            return _readBuffer
        }

    @InternalAPI
    override val writeBuffer: Sink
        get() {
            closedCause?.let { throw it }
            if (isClosedForWrite) {
                throw IOException("Channel is closed for write")
            }
            return _writeBuffer
        }

    override val closedCause: Throwable?
        get() = _closedCause.value?.cause

    override val isClosedForWrite: Boolean
        get() = _closedCause.value != null

    override val isClosedForRead: Boolean
        get() = (closedCause != null) || (isClosedForWrite && flushBufferSize == 0 && _readBuffer.exhausted())

    @OptIn(InternalAPI::class)
    override suspend fun awaitContent(min: Int): Boolean {
        rethrowCloseCauseIfNeeded()
        if (flushBufferSize + _readBuffer.size >= min) return true

        sleepForRead(min)

        if (_readBuffer.size < CHANNEL_MAX_SIZE) moveFlushToReadBuffer()
        return _closedCause.value == null
    }

    @OptIn(InternalAPI::class)
    private fun moveFlushToReadBuffer() {
        synchronized(mutex) {
            flushBuffer.transferTo(_readBuffer)
            flushBufferSize = 0
        }

        readWriteLock.resumeWrite()
    }

    @OptIn(InternalAPI::class)
    override suspend fun flush() {
        rethrowCloseCauseIfNeeded()

        flushWriteBuffer()
        if (flushBufferSize < CHANNEL_MAX_SIZE) return

        sleepForWrite()
    }

    @InternalAPI
    public override fun flushWriteBuffer() {
        if (_writeBuffer.exhausted()) return

        synchronized(mutex) {
            val count = _writeBuffer.size.toInt()
            flushBuffer.transferFrom(_writeBuffer)
            flushBufferSize += count
        }

        readWriteLock.resumeRead()
    }

    @OptIn(InternalAPI::class)
    override fun close() {
        flushWriteBuffer()

        // It's important to flush before we have closedCause set
        if (!_closedCause.compareAndSet(null, CLOSED)) return
        readWriteLock.close()
    }

    @OptIn(InternalAPI::class)
    override suspend fun flushAndClose() {
        runCatching {
            flush()
        }

        if (!_closedCause.compareAndSet(null, CLOSED)) return
        readWriteLock.close()
    }

    @OptIn(InternalAPI::class)
    override fun cancel(cause: Throwable?) {
        if (_closedCause.value != null) return

        val closedToken = CloseToken(cause)
        _closedCause.compareAndSet(null, closedToken)
        val actualCause = closedToken.cause

        readWriteLock.close(actualCause)
    }

    override fun toString(): String = "ByteChannel[${hashCode()}]"

    @OptIn(InternalAPI::class)
    private suspend fun sleepForRead(min: Int) {
        while (flushBufferSize + _readBuffer.size < min && _closedCause.value == null) {
            readWriteLock.waitForRead()
        }
    }

    @OptIn(InternalAPI::class)
    private suspend fun sleepForWrite() {
        while (flushBufferSize >= CHANNEL_MAX_SIZE) {
            readWriteLock.waitForWrite()
        }
    }

}
