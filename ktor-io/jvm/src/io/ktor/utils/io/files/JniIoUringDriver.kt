/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.utils.io.files

import kotlinx.io.Buffer
import kotlinx.io.IOException
import kotlinx.io.UnsafeIoApi
import kotlinx.io.unsafe.UnsafeBufferOperations
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap

internal object JniIoUringDriver : IoUringDriver {
    private val buffers = ConcurrentHashMap<Long, ByteBuffer>()

    override fun createRing(entries: Int): Long {
        val ring = IoUringNative.ringCreate(entries)
        if (ring == 0L) throw IOException("Failed to create io_uring ring")
        return ring
    }

    override fun destroyRing(ring: Long) {
        IoUringNative.ringDestroy(ring)
    }

    override fun submitOpen(ring: Long, path: String, slot: Int): Int =
        IoUringNative.submitOpen(ring, path, slot)

    override fun submitRead(ring: Long, fd: Int, address: Long, length: Int, offset: Long, slot: Int): Int =
        IoUringNative.submitRead(ring, fd, address, length, offset, slot)

    override fun submitClose(ring: Long, fd: Int, slot: Int): Int =
        IoUringNative.submitClose(ring, fd, slot)

    override fun submitCancel(ring: Long, slot: Int): Int = IoUringNative.submitCancel(ring, slot)

    override fun submitWake(ring: Long): Int = IoUringNative.submitWake(ring)

    override fun waitCompletions(ring: Long, slots: IntArray, results: IntArray): Int =
        IoUringNative.waitCompletions(ring, slots, results)

    override fun allocate(size: Int): Long {
        val buffer =
            IoUringNative.allocateBuffer(size) ?: throw OutOfMemoryError("Failed to allocate io_uring read buffer")
        val address = IoUringNative.bufferAddress(buffer)
        if (address == 0L) {
            IoUringNative.freeBuffer(buffer)
            error("Failed to get io_uring read buffer address")
        }
        if (buffers.putIfAbsent(address, buffer) != null) {
            IoUringNative.freeBuffer(buffer)
            error("Duplicate io_uring read buffer address")
        }
        return address
    }

    override fun free(address: Long) {
        buffers.remove(address)?.let(IoUringNative::freeBuffer)
    }

    override fun copyToBuffer(address: Long, length: Int, sink: Buffer) {
        val source = checkNotNull(buffers[address]) { "Unknown io_uring read buffer address" }.duplicate()
        source.clear()
        var remaining = length
        while (remaining > 0) {
            @OptIn(UnsafeIoApi::class)
            UnsafeBufferOperations.writeToTail(sink, length) { bytes, startIndexInclusive, endIndexExclusive ->
                val count = minOf(endIndexExclusive - startIndexInclusive, remaining)
                source.get(bytes, startIndexInclusive, endIndexExclusive - startIndexInclusive)
                remaining -= count
                count
            }
        }
    }
}
