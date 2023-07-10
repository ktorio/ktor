package io.ktor.utils.io

import io.ktor.utils.io.pool.*
import kotlin.test.*

internal expect fun identityHashCode(instance: Any): Int

expect class VerifyingObjectPool<T : Any>(delegate: ObjectPool<T>) : VerifyingPoolBase<T>

abstract class VerifyingPoolBase<T : Any> constructor(private val delegate: ObjectPool<T>) : ObjectPool<T> by delegate {
//    protected abstract val allocated: MutableSet<IdentityWrapper<T>> // KT-60227

//    val used: Int // KT-60227
//        get() = allocated.size

    final override fun borrow(): T {
        val instance = delegate.borrow()
//        if (!allocated.add(IdentityWrapper(instance))) { // KT-60227
//            throw AssertionError("Instance $instance has been provided by the pool twice")
//        }
        return instance
    }

    final override fun recycle(instance: T) {
//        if (!allocated.remove(IdentityWrapper(instance))) { // KT-60227
//            throw AssertionError(
//                "Instance $instance hasn't been borrowed but tried to recycle (possibly double recycle)"
//            )
//        }
        delegate.recycle(instance)
    }

    fun assertEmpty() {
//        assertEquals(0, allocated.size, "There are remaining unreleased buffers, ") // KT-60227
    }

    protected class IdentityWrapper<T : Any>(private val instance: T) {
        override fun equals(other: Any?): Boolean {
            if (other !is IdentityWrapper<*>) return false
            return other.instance === this.instance
        }

        override fun hashCode() = identityHashCode(instance)
    }
}
