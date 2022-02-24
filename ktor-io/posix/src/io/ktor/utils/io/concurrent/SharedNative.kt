/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.utils.io.concurrent

import io.ktor.utils.io.core.internal.*
import kotlinx.atomicfu.*
import kotlin.native.concurrent.*
import kotlin.properties.*
import kotlin.reflect.*

/**
 * Allows to create mutate property with frozen value.
 * Please note that any assigned value will be frozen.
 *
 * Usage:
 * ```kotlin
 * var myCounter by shared(0)
 * ```
 */
@Suppress("NOTHING_TO_INLINE")
public actual inline fun <T> shared(value: T): ReadWriteProperty<Any, T> = object : ReadWriteProperty<Any, T> {
    private var reference = atomic(value)

    init {
        freeze()
    }

    override fun getValue(thisRef: Any, property: KProperty<*>): T {
        return reference.value
    }

    override fun setValue(thisRef: Any, property: KProperty<*>, value: T) {
        reference.value = value
    }
}

/**
 * Allow to create thread local reference without freezing.
 * Please note that reference is thread-local only in kotlin-native. Otherwise it will be simple [value] reference.
 *
 * This reference is allowed to use only from creation thread. Otherwise it will return null.
 */
@DangerousInternalIoApi
public actual fun <T : Any> threadLocal(value: T): ReadOnlyProperty<Any, T?> {
    val threadLocal = ThreadLocalValue(value)

    return object : ReadOnlyProperty<Any, T?> {
        init {
            freeze()
        }

        override fun getValue(thisRef: Any, property: KProperty<*>): T? = threadLocal.value
    }
}
