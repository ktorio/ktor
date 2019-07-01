package io.ktor.utils.io.tests

import io.ktor.utils.io.pool.*

@SymbolName("Kotlin_Any_hashCode")
private external fun identityHashCodeImpl(any: Any?): Int

@Suppress("NOTHING_TO_INLINE")
internal inline actual fun identityHashCode(instance: Any): Int = identityHashCodeImpl(instance)

actual class VerifyingObjectPool<T : Any> actual constructor(delegate: ObjectPool<T>) : VerifyingPoolBase<T>(delegate) {
    override val allocated = HashSet<IdentityWrapper<T>>()
}
