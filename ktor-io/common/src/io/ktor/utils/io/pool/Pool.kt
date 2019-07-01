package io.ktor.utils.io.pool

import kotlinx.atomicfu.*
import io.ktor.utils.io.core.*
import kotlin.jvm.*

interface ObjectPool<T : Any> : Closeable {
    /**
     * Pool capacity
     */
    val capacity: Int

    /**
     * borrow an instance. Pool can recycle an old instance or create a new one
     */
    fun borrow(): T

    /**
     * Recycle an instance. Should be recycled what was borrowed before otherwise could fail
     */
    fun recycle(instance: T)

    /**
     * Dispose the whole pool. None of borrowed objects could be used after the pool gets disposed
     * otherwise it can result in undefined behaviour
     */
    fun dispose()

    /**
     * Does pool dispose
     */
    override fun close() = dispose()
}

/**
 * A pool implementation of zero capacity that always creates new instances
 */
abstract class NoPoolImpl<T : Any> : ObjectPool<T> {
    override val capacity: Int
        get() = 0

    override fun recycle(instance: T) {
    }

    override fun dispose() {
    }
}

/**
 * A pool that produces at most one instance
 */
abstract class SingleInstancePool<T : Any> : ObjectPool<T> {
    private val borrowed = atomic(0)
    private val disposed = atomic(false)

    @Volatile
    private var instance: T? = null

    /**
     * Creates a new instance of [T]
     */
    protected abstract fun produceInstance(): T

    /**
     * Dispose [instance] and release it's resources
     */
    protected abstract fun disposeInstance(instance: T)

    final override val capacity: Int get() = 1

    final override fun borrow(): T {
        borrowed.update {
            if (it != 0) throw IllegalStateException("Instance is already consumed")
            1
        }

        val instance = produceInstance()
        this.instance = instance

        return instance
    }

    final override fun recycle(instance: T) {
        if (this.instance !== instance) {
            if (this.instance == null && borrowed.value != 0) {
                throw IllegalStateException("Already recycled or an irrelevant instance tried to be recycled")
            }

            throw IllegalStateException("Unable to recycle irrelevant instance")
        }

        this.instance = null

        if (!disposed.compareAndSet(false, true)) {
            throw IllegalStateException("An instance is already disposed")
        }

        disposeInstance(instance)
    }

    final override fun dispose() {
        if (disposed.compareAndSet(false, true)) {
            val instance = this.instance ?: return
            this.instance = null

            disposeInstance(instance)
        }
    }
}

/**
 * Default object pool implementation.
 */
expect abstract class DefaultPool<T : Any>(capacity: Int) : ObjectPool<T> {
    /**
     * Pool capacity.
     */
    final override val capacity: Int

    /**
     * Creates a new instance of [T]
     */
    protected abstract fun produceInstance(): T

    /**
     * Dispose [instance] and release it's resources
     */
    protected open fun disposeInstance(instance: T)

    /**
     * Clear [instance]'s state before reuse: reset pointers, counters and so on
     */
    protected open fun clearInstance(instance: T): T

    /**
     * Validate [instance] of [T]. Could verify that the object has been borrowed from this pool
     */
    protected open fun validateInstance(instance: T)

    final override fun borrow(): T

    final override fun recycle(instance: T)

    final override fun dispose()
}

/**
 * Borrows and instance of [T] from the pool, invokes [block] with it and finally recycles it
 */
@Deprecated("Use useInstance instead", ReplaceWith("useInstance(block)"))
inline fun <T : Any, R> ObjectPool<T>.useBorrowed(block: (T) -> R): R {
    return useInstance(block)
}

/**
 * Borrows and instance of [T] from the pool, invokes [block] with it and finally recycles it
 */
inline fun <T : Any, R> ObjectPool<T>.useInstance(block: (T) -> R): R {
    val instance = borrow()
    try {
        return block(instance)
    } finally {
        recycle(instance)
    }
}

