package io.ktor.utils.io.tests

import io.ktor.utils.io.pool.*

// TODO: KT-21487: Support common way to get identity hash code in legacy and IR Kotlin/JS backends.
@Suppress("NOTHING_TO_INLINE")
internal inline actual fun identityHashCode(instance: Any): Int = instance.hashCode()

actual class VerifyingObjectPool<T : Any> actual constructor(delegate: ObjectPool<T>) : VerifyingPoolBase<T>(delegate) {
    override val allocated = HashSet<IdentityWrapper<T>>()
}
