/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.utils.io.files

import io.ktor.io.interop.liburing.ktor_io_uring_create
import io.ktor.io.interop.liburing.ktor_io_uring_destroy
import io.ktor.io.interop.liburing.ktor_io_uring_submit_cancel
import io.ktor.io.interop.liburing.ktor_io_uring_submit_close
import io.ktor.io.interop.liburing.ktor_io_uring_submit_open
import io.ktor.io.interop.liburing.ktor_io_uring_submit_read
import io.ktor.io.interop.liburing.ktor_io_uring_submit_wake
import io.ktor.io.interop.liburing.ktor_io_uring_wait
import io.ktor.utils.io.core.writeFully
import kotlinx.cinterop.*
import kotlinx.io.Buffer
import kotlinx.io.IOException

@OptIn(ExperimentalForeignApi::class)
internal object CInteropIoUringDriver : IoUringDriver {
    override fun createRing(entries: Int): Long {
        val ring = ktor_io_uring_create(entries.toUInt())
        if (ring <= 0L) throw IOException("Failed to create io_uring ring: errno=${-ring}")
        return ring
    }

    override fun destroyRing(ring: Long) {
        ktor_io_uring_destroy(ring.toULong())
    }

    override fun submitOpen(ring: Long, path: String, slot: Int): Int =
        ktor_io_uring_submit_open(ring.toULong(), path, slot)

    override fun submitRead(ring: Long, fd: Int, address: Long, length: Int, offset: Long, slot: Int): Int =
        ktor_io_uring_submit_read(ring.toULong(), fd, address.toULong(), length, offset.toULong(), slot)

    override fun submitClose(ring: Long, fd: Int, slot: Int): Int =
        ktor_io_uring_submit_close(ring.toULong(), fd, slot)

    override fun submitCancel(ring: Long, slot: Int): Int =
        ktor_io_uring_submit_cancel(ring.toULong(), slot)

    override fun submitWake(ring: Long): Int = ktor_io_uring_submit_wake(ring.toULong())

    override fun waitCompletions(ring: Long, slots: IntArray, results: IntArray): Int = memScoped {
        val slot = alloc<kotlinx.cinterop.IntVar>()
        val result = alloc<kotlinx.cinterop.IntVar>()
        val status = ktor_io_uring_wait(ring.toULong(), slot.ptr, result.ptr)
        if (status < 0) {
            status
        } else {
            slots[0] = slot.value
            results[0] = result.value
            1
        }
    }

    override fun allocate(size: Int): Long = nativeHeap.allocArray<ByteVar>(size).rawValue.toLong()

    override fun free(address: Long) {
        if (address != 0L) nativeHeap.free(address.toCPointer<ByteVar>()!!.rawValue)
    }

    override fun copyToBuffer(address: Long, length: Int, sink: Buffer) {
        sink.writeFully(address.toCPointer<ByteVar>()!!, 0L, length.toLong())
    }
}
