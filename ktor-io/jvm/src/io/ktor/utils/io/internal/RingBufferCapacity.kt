package io.ktor.utils.io.internal

import kotlin.jvm.*

@Suppress("LocalVariableName")
internal class RingBufferCapacity(private val totalCapacity: Int) {
    @Volatile
    @JvmField
    var availableForRead = 0

    @Volatile @JvmField
    var availableForWrite = totalCapacity

    @Volatile @JvmField
    var pendingToFlush = 0

    // concurrent unsafe!
    fun resetForWrite() {
        availableForRead = 0
        availableForWrite = totalCapacity
        pendingToFlush = 0
    }

    fun resetForRead() {
        availableForRead = totalCapacity
        availableForWrite = 0
        pendingToFlush = 0
    }

    fun tryReadExact(n: Int): Boolean {
        val AvailableForRead = AvailableForRead
        while (true) {
            val remaining = availableForRead
            if (remaining < n) return false
            if (AvailableForRead.compareAndSet(this, remaining, remaining - n)) return true
        }
    }

    fun tryReadAtMost(n: Int): Int {
        val AvailableForRead = AvailableForRead
        while (true) {
            val remaining = availableForRead
            val delta = minOf(n, remaining)
            if (delta == 0) return 0
            if (AvailableForRead.compareAndSet(this, remaining, remaining - delta)) return delta
        }
    }

    fun tryWriteAtLeast(n: Int): Int {
        val AvailableForWrite = AvailableForWrite
        while (true) {
            val remaining = availableForWrite
            if (remaining < n) return 0
            if (AvailableForWrite.compareAndSet(this, remaining, 0)) return remaining
        }
    }

    fun tryWriteExact(n: Int): Boolean {
        val AvailableForWrite = AvailableForWrite
        while (true) {
            val remaining = availableForWrite
            if (remaining < n) return false
            if (AvailableForWrite.compareAndSet(this, remaining, remaining - n)) return true
        }
    }

    fun tryWriteAtMost(n: Int): Int {
        val AvailableForWrite = AvailableForWrite

        while (true) {
            val remaining = availableForWrite
            val delta = minOf(n, remaining)
            if (delta == 0) return 0
            if (AvailableForWrite.compareAndSet(this, remaining, remaining - delta)) return delta
        }
    }

    fun completeRead(n: Int) {
        val totalCapacity = totalCapacity
        val AvailableForWrite = AvailableForWrite

        while (true) {
            val remaining = availableForWrite
            val update = remaining + n
            if (update > totalCapacity) completeReadOverflow(remaining, update, n)
            if (AvailableForWrite.compareAndSet(this, remaining, update)) break
        }
    }

    private fun completeReadOverflow(remaining: Int, update: Int, n: Int): Nothing {
        throw IllegalArgumentException("Completed read overflow: $remaining + $n = $update > $totalCapacity")
    }

    fun completeWrite(n: Int) {
        val totalCapacity = totalCapacity
        val PendingToFlush = PendingToFlush

        while (true) {
            val pending = pendingToFlush
            val update = pending + n
            if (update > totalCapacity) completeReadOverflow(pending, n)
            if (PendingToFlush.compareAndSet(this, pending, update)) break
        }
    }

    private fun completeReadOverflow(pending: Int, n: Int): Nothing {
        throw IllegalArgumentException("Complete write overflow: $pending + $n > $totalCapacity")
    }

    /**
     * @return true if there are bytes available for read after flush
     */
    fun flush(): Boolean {
        val AvailableForRead = AvailableForRead
        val pending = PendingToFlush.getAndSet(this, 0)
        while (true) {
            val remaining = availableForRead
            val update = remaining + pending
            if (remaining == update || AvailableForRead.compareAndSet(this, remaining, update)) {
                return update > 0
            }
        }
    }

    fun tryLockForRelease(): Boolean {
        val AvailableForWrite = AvailableForWrite
        while (true) {
            val remaining = availableForWrite
            if (pendingToFlush > 0 || availableForRead > 0 || remaining != totalCapacity) return false
            if (AvailableForWrite.compareAndSet(this, remaining, 0)) return true
        }
    }

    /**
     * Make all writers to fail to write any more bytes
     * Use only during failure termination
     */
    fun forceLockForRelease() {
        AvailableForWrite.getAndSet(this, 0)
    }

    fun isEmpty(): Boolean = availableForWrite == totalCapacity
    fun isFull(): Boolean = availableForWrite == 0

    companion object {
        // todo: replace with atomicfu, remove companion object
        private val AvailableForRead = intUpdater(RingBufferCapacity::availableForRead)
        private val AvailableForWrite = intUpdater(RingBufferCapacity::availableForWrite)
        private val PendingToFlush = intUpdater(RingBufferCapacity::pendingToFlush)
    }
}