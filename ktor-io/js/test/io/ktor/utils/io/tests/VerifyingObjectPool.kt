package io.ktor.utils.io.tests

import io.ktor.utils.io.pool.*

@Suppress("NOTHING_TO_INLINE")
internal inline actual fun identityHashCode(instance: Any): Int = js("Kotlin").identityHashCode(instance) as Int

actual class VerifyingObjectPool<T : Any> actual constructor(delegate: ObjectPool<T>) : VerifyingPoolBase<T>(delegate) {
    override val allocated = HashSet<IdentityWrapper<T>>()
}
