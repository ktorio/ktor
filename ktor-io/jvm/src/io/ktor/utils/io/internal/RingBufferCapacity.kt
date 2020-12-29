package io.ktor.utils.io.internal

import kotlinx.atomicfu.*

@Suppress("LocalVariableName")
internal class RingBufferCapacity(private val totalCapacity: Int) {
    val _availableForRead: AtomicInt = atomic(0)
    val _availableForWrite: AtomicInt = atomic(totalCapacity)
    val _pendingToFlush: AtomicInt = atomic(0)

    inline var availableForRead: Int
        get() = _availableForRead.value
        set(value) {
            _availableForRead.value = value
        }

    inline var availableForWrite: Int
        get() = _availableForWrite.value
        set(value) {
            _availableForWrite.value = value
        }

    inline var pendingToFlush: Int
        get() = _pendingToFlush.value
        set(value) {
            _pendingToFlush.value = value
        }

    // concurrent unsafe!
    fun resetForWrite() {
        _availableForRead.value = 0
        _availableForWrite.value = totalCapacity
        _pendingToFlush.value = 0
    }

    fun resetForRead() {
        _availableForRead.value = totalCapacity
        _availableForWrite.value = 0
        _pendingToFlush.value = 0
    }

    fun tryReadAtLeast(n: Int): Int {
        while (true) {
            val remaining = _availableForRead.value
            if (remaining < n) return 0
            if (_availableForRead.compareAndSet(remaining, 0)) return remaining
        }
    }

    fun tryReadExact(n: Int): Boolean {
        while (true) {
            val remaining = _availableForRead.value
            if (remaining < n) return false
            if (_availableForRead.compareAndSet(remaining, remaining - n)) return true
        }
    }

    fun tryReadAtMost(n: Int): Int {
        while (true) {
            val remaining = _availableForRead.value
            val delta = minOf(n, remaining)
            if (delta == 0) return 0
            if (_availableForRead.compareAndSet(remaining, remaining - delta)) return delta
        }
    }

    fun tryWriteAtLeast(n: Int): Int {
        while (true) {
            val remaining = _availableForWrite.value
            if (remaining < n) return 0
            if (_availableForWrite.compareAndSet(remaining, 0)) return remaining
        }
    }

    fun tryWriteExact(n: Int): Boolean {
        while (true) {
            val remaining = _availableForWrite.value
            if (remaining < n) return false
            if (_availableForWrite.compareAndSet(remaining, remaining - n)) return true
        }
    }

    fun tryWriteAtMost(n: Int): Int {
        while (true) {
            val remaining = _availableForWrite.value
            val delta = minOf(n, remaining)
            if (delta == 0) return 0
            if (_availableForWrite.compareAndSet(remaining, remaining - delta)) return delta
        }
    }

    fun completeRead(n: Int) {
        val totalCapacity = totalCapacity
        while (true) {
            val remaining = _availableForWrite.value
            val update = remaining + n
            if (update > totalCapacity) completeReadOverflow(remaining, update, n)
            if (_availableForWrite.compareAndSet(remaining, update)) break
        }
    }

    private fun completeReadOverflow(remaining: Int, update: Int, n: Int): Nothing {
        throw IllegalArgumentException("Completed read overflow: $remaining + $n = $update > $totalCapacity")
    }

    fun completeWrite(n: Int) {
        val totalCapacity = totalCapacity

        while (true) {
            val pending = _pendingToFlush.value
            val update = pending + n
            if (update > totalCapacity) completeReadOverflow(pending, n)
            if (_pendingToFlush.compareAndSet(pending, update)) break
        }
    }

    private fun completeReadOverflow(pending: Int, n: Int): Nothing {
        throw IllegalArgumentException("Complete write overflow: $pending + $n > $totalCapacity")
    }

    /**
     * @return true if there are bytes available for read after flush
     */
    fun flush(): Boolean {
        val pending = _pendingToFlush.getAndSet(0)
        while (true) {
            val remaining = _availableForRead.value
            val update = remaining + pending
            if (remaining == update || _availableForRead.compareAndSet(remaining, update)) {
                return update > 0
            }
        }
    }

    fun tryLockForRelease(): Boolean {
        while (true) {
            val remaining = _availableForWrite.value
            if (pendingToFlush > 0 || availableForRead > 0 || remaining != totalCapacity) return false
            if (_availableForWrite.compareAndSet(remaining, 0)) return true
        }
    }

    /**
     * Make all writers to fail to write any more bytes
     * Use only during failure termination
     */
    fun forceLockForRelease() {
        _availableForWrite.getAndSet(0)
    }

    fun isEmpty(): Boolean = _availableForWrite.value == totalCapacity

    fun isFull(): Boolean = _availableForWrite.value == 0

    override fun toString(): String =
        "RingBufferCapacity[read: $availableForRead, write: $availableForWrite, flush: $pendingToFlush, capacity: $totalCapacity]"
}
