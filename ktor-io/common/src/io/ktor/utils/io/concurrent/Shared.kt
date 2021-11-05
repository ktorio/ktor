/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.utils.io.concurrent

import kotlinx.atomicfu.*
import kotlinx.atomicfu.locks.*
import kotlin.properties.*
import kotlin.reflect.*

/**
 * Allows creating mutate property with frozen value.
 * Please note that any assigned value will be frozen.
 *
 * Usage:
 * ```kotlin
 * var myCounter by shared(0)
 * ```
 */
public expect inline fun <T> shared(value: T): ReadWriteProperty<Any, T>

/**
 * Allow creating thread local reference without freezing.
 * Please note that reference is thread-local only in kotlin-native. Otherwise it will be simple [value] reference.
 *
 * It will have value in creation thread and null otherwise.
 */
public expect fun <T : Any> threadLocal(value: T): ReadOnlyProperty<Any, T?>

/**
 * Allows creating thread safe lazy to use with Kotlin-Native.
 */
public fun <T : Any> sharedLazy(
    function: () -> T
): ReadOnlyProperty<Any, T> = object : ReadOnlyProperty<Any, T>, SynchronizedObject() {
    val value = atomic<T?>(null)

    override fun getValue(thisRef: Any, property: KProperty<*>): T = synchronized(this) {
        if (value.value == null) {
            value.value = function()
        }

        return value.value!!
    }
}
