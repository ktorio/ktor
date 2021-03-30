/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.utils.io

import io.ktor.utils.io.pool.*

@SymbolName("Kotlin_Any_hashCode")
private external fun identityHashCodeImpl(any: Any?): Int

@Suppress("NOTHING_TO_INLINE")
internal actual inline fun identityHashCode(instance: Any): Int = identityHashCodeImpl(instance)

actual class VerifyingObjectPool<T : Any> actual constructor(delegate: ObjectPool<T>) : VerifyingPoolBase<T>(delegate) {
    override val allocated = HashSet<IdentityWrapper<T>>()
}
