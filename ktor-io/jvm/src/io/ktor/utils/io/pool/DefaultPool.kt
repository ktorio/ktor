package io.ktor.utils.io.pool

import io.ktor.utils.io.utils.*
import java.util.concurrent.atomic.*
import kotlin.jvm.*

private const val MULTIPLIER = 4
private const val PROBE_COUNT = 8 // number of attempts to find a slot
private const val MAGIC = 2654435769.toInt() // fractional part of golden ratio
private const val MAX_CAPACITY = Int.MAX_VALUE / MULTIPLIER

actual abstract class DefaultPool<T : Any> actual constructor(actual final override val capacity: Int) : ObjectPool<T> {
    init {
        require(capacity > 0) { "capacity should be positive but it is $capacity" }
        require(capacity <= MAX_CAPACITY) { "capacity should be less or equal to $MAX_CAPACITY but it is $capacity"}
    }

    protected actual abstract fun produceInstance(): T // factory
    protected actual open fun clearInstance(instance: T) = instance // optional cleaning of poped items
    protected actual open fun validateInstance(instance: T) {} // optional validation for recycled items
    protected actual open fun disposeInstance(instance: T) {} // optional destruction of unpoolable items

    @Volatile
    private var top: Long = 0L

    // closest power of 2 that is equal or larger than capacity * MULTIPLIER
    private val maxIndex = Integer.highestOneBit(capacity * MULTIPLIER - 1) * 2
    private val shift = Integer.numberOfLeadingZeros(maxIndex) + 1 // for hash function

    // zero index is reserved for both
    private val instances = AtomicReferenceArray<T?>(maxIndex + 1)
    private val next = IntArray(maxIndex + 1)

    actual final override fun borrow(): T =
            tryPop()?.let { clearInstance(it) } ?: produceInstance()

    actual final override fun recycle(instance: T) {
        validateInstance(instance)
        if (!tryPush(instance)) disposeInstance(instance)
    }

    actual final override fun dispose() {
        while (true) {
            val instance = tryPop() ?: return
            disposeInstance(instance)
        }
    }

    private fun tryPush(instance: T): Boolean {
        var index = ((System.identityHashCode(instance) * MAGIC) ushr shift) + 1
        repeat (PROBE_COUNT) {
            if (instances.compareAndSet(index, null, instance)) {
                pushTop(index)
                return true
            }
            if (--index == 0) index = maxIndex
        }
        return false
    }

    private fun tryPop(): T? {
        val index = popTop()
        return if (index == 0) null else instances.getAndSet(index, null)
    }

    private fun pushTop(index: Int) {
        require(index > 0) { "index should be positive" }
        while (true) { // lock-free loop on top
            val top = this.top // volatile read
            val topVersion = (top shr 32 and 0xffffffffL) + 1L
            val topIndex = (top and 0xffffffffL).toInt()
            val newTop = topVersion shl 32 or index.toLong()
            next[index] = topIndex
            if (Top.compareAndSet(this, top, newTop)) return
        }
    }

    private fun popTop(): Int {
        while (true) { // lock-free loop on top
            val top = this.top // volatile read
            if (top == 0L) return 0
            val newVersion = (top shr 32 and 0xffffffffL) + 1L
            val topIndex = (top and 0xffffffffL).toInt()
            if (topIndex == 0) return 0
            val next = next[topIndex]
            val newTop = newVersion shl 32 or next.toLong()
            if (Top.compareAndSet(this, top, newTop)) return topIndex
        }
    }

    companion object {
        // todo: replace with atomicfu, remove companion object
        private val Top = longUpdater(DefaultPool<*>::top)
    }
}
