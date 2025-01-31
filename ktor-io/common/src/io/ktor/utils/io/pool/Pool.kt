/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.utils.io.pool

import kotlinx.atomicfu.*

public interface ObjectPool<T : Any> : AutoCloseable {
    /**
     * Pool capacity
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.utils.io.pool.ObjectPool.capacity)
     */
    public val capacity: Int

    /**
     * borrow an instance. Pool can recycle an old instance or create a new one
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.utils.io.pool.ObjectPool.borrow)
     */
    public fun borrow(): T

    /**
     * Recycle an instance. Should be recycled what was borrowed before otherwise could fail
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.utils.io.pool.ObjectPool.recycle)
     */
    public fun recycle(instance: T)

    /**
     * Dispose the whole pool. None of borrowed objects could be used after the pool gets disposed
     * otherwise it can result in undefined behaviour
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.utils.io.pool.ObjectPool.dispose)
     */
    public fun dispose()

    /**
     * Does pool dispose
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.utils.io.pool.ObjectPool.close)
     */
    override fun close() {
        dispose()
    }
}

/**
 * A pool implementation of zero capacity that always creates new instances
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.utils.io.pool.NoPoolImpl)
 */
public abstract class NoPoolImpl<T : Any> : ObjectPool<T> {
    override val capacity: Int
        get() = 0

    override fun recycle(instance: T): Unit = Unit

    override fun dispose(): Unit = Unit
}

/**
 * A pool that produces at most one instance
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.utils.io.pool.SingleInstancePool)
 */
public abstract class SingleInstancePool<T : Any> : ObjectPool<T> {
    private val borrowed = atomic(0)
    private val disposed = atomic(false)

    private val instance = atomic<T?>(null)

    /**
     * Creates a new instance of [T]
     */
    protected abstract fun produceInstance(): T

    /**
     * Dispose [instance] and release its resources
     */
    protected abstract fun disposeInstance(instance: T)

    final override val capacity: Int get() = 1

    final override fun borrow(): T {
        borrowed.update {
            if (it != 0) error("Instance is already consumed")
            1
        }

        val instance = produceInstance()
        this.instance.value = instance

        return instance
    }

    final override fun recycle(instance: T) {
        if (this.instance.value !== instance) {
            if (this.instance.value == null && borrowed.value != 0) {
                error("Already recycled or an irrelevant instance tried to be recycled")
            }

            error("Unable to recycle irrelevant instance")
        }

        this.instance.value = null

        if (!disposed.compareAndSet(false, true)) {
            error("An instance is already disposed")
        }

        disposeInstance(instance)
    }

    final override fun dispose() {
        if (disposed.compareAndSet(false, true)) {
            val value = instance.value ?: return
            instance.value = null

            disposeInstance(value)
        }
    }
}

/**
 * Default object pool implementation.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.utils.io.pool.DefaultPool)
 */
public expect abstract class DefaultPool<T : Any>(capacity: Int) : ObjectPool<T> {
    /**
     * Pool capacity.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.utils.io.pool.DefaultPool.capacity)
     */
    final override val capacity: Int

    /**
     * Creates a new instance of [T]
     */
    protected abstract fun produceInstance(): T

    /**
     * Dispose [instance] and release its resources
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
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.utils.io.pool.useBorrowed)
 */
@Deprecated("Use useInstance instead", ReplaceWith("useInstance(block)"))
public inline fun <T : Any, R> ObjectPool<T>.useBorrowed(block: (T) -> R): R {
    return useInstance(block)
}

/**
 * Borrows and instance of [T] from the pool, invokes [block] with it and finally recycles it
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.utils.io.pool.useInstance)
 */
public inline fun <T : Any, R> ObjectPool<T>.useInstance(block: (T) -> R): R {
    val instance = borrow()
    try {
        return block(instance)
    } finally {
        recycle(instance)
    }
}
