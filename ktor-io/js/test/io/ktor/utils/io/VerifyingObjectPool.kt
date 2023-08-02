/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.utils.io

import io.ktor.utils.io.pool.*

@Suppress("NOTHING_TO_INLINE")
internal actual inline fun identityHashCode(instance: Any): Int = instance.hashCode()

actual class VerifyingObjectPool<T : Any> actual constructor(delegate: ObjectPool<T>) : VerifyingPoolBase<T>(delegate) {
    actual override val allocated: MutableSet<IdentityWrapper<T>> = HashSet<IdentityWrapper<T>>()
}
