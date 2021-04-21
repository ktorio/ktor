package io.ktor.utils.io.internal

import kotlinx.atomicfu.*

@Suppress("LocalVariableName")
internal class RingBufferCapacity(private val totalCapacity: Int) {
    private val _availableForRead: AtomicInt = atomic(0)
    private val _availableForWrite: AtomicInt = atomic(totalCapacity)
    private val _pendingToFlush: AtomicInt = atomic(0)

    inline var availableForRead: Int
        get() = _availableForRead.value
        private set(value) {
            _availableForRead.value = value
        }

    inline var availableForWrite: Int
        get() = _availableForWrite.value
        private set(value) {
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
        _pendingToFlush.value = 0
        _availableForWrite.value = totalCapacity
    }

    fun resetForRead() {
        _availableForRead.value = totalCapacity
        _availableForWrite.value = 0
        _pendingToFlush.value = 0
    }

    fun tryReadAtLeast(n: Int): Int {
        val available = _availableForRead.getAndUpdate { remaining ->
            if (remaining < n) return 0
            0
        }
        return available
    }

    fun tryReadExact(n: Int): Boolean {
        _availableForRead.update { remaining ->
            if (remaining < n) return false
            remaining - n
        }
        return true
    }

    fun tryReadAtMost(n: Int): Int {
        val available = _availableForRead.getAndUpdate { remaining ->
            val delta = minOf(n, remaining)
            if (delta == 0) return 0
            remaining - delta
        }
        return minOf(n, available)
    }

    fun tryWriteAtLeast(n: Int): Int {
        val available = _availableForWrite.getAndUpdate { remaining ->
            if (remaining < n) return 0
            0
        }
        return available
    }

    fun tryWriteExact(n: Int): Boolean {
        _availableForWrite.update { remaining ->
            if (remaining < n) return false
            remaining - n
        }
        return true
    }

    fun tryWriteAtMost(n: Int): Int {
        val available = _availableForWrite.getAndUpdate { remaining ->
            val delta = minOf(n, remaining)
            if (delta == 0) return 0
            remaining - delta
        }
        return minOf(n, available)
    }

    fun completeRead(n: Int) {
        _availableForWrite.update { remaining ->
            val update = remaining + n
            if (update > totalCapacity) completeReadOverflow(remaining, update, n)
            update
        }
    }

    private fun completeReadOverflow(remaining: Int, update: Int, n: Int): Nothing {
        throw IllegalArgumentException("Completed read overflow: $remaining + $n = $update > $totalCapacity")
    }

    fun completeWrite(n: Int) {
        _pendingToFlush.update { pending ->
            val update = pending + n
            if (update > totalCapacity) completeWriteOverflow(pending, n)
            update
        }
    }

    private fun completeWriteOverflow(pending: Int, n: Int): Nothing {
        throw IllegalArgumentException("Complete write overflow: $pending + $n > $totalCapacity")
    }

    /**
     * @return true if there are bytes available for read after flush
     */
    fun flush(): Boolean {
        val pending = _pendingToFlush.getAndSet(0)
        return if (pending == 0) {
            _availableForRead.value > 0
        } else {
            return _availableForRead.addAndGet(pending) > 0
        }
    }

    fun tryLockForRelease(): Boolean {
        _availableForWrite.update { remaining ->
            if (pendingToFlush > 0 || availableForRead > 0 || remaining != totalCapacity) return false
            0
        }
        return true
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
        "RingBufferCapacity[read: $availableForRead, write: $availableForWrite, " +
            "flush: $pendingToFlush, capacity: $totalCapacity]"
}
