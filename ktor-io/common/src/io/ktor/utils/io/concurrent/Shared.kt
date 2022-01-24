/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

@file:Suppress("UNUSED_PARAMETER", "DeprecatedCallableAddReplaceWith")

package io.ktor.utils.io.concurrent

import kotlin.properties.*

/**
 * Allows creating mutate property with frozen value.
 * Please note that any assigned value will be frozen.
 *
 * Usage:
 * ```kotlin
 * var myCounter by shared(0)
 * ```
 */
@Deprecated(
    "Is obsolete in new memory model.",
    level = DeprecationLevel.ERROR
)
public fun <T> shared(value: T): ReadWriteProperty<Any, T> = error("Obsolete in new memory model")

/**
 * Allow creating thread local reference without freezing.
 * Please note that reference is thread-local only in kotlin-native. Otherwise, it will be simple [value] reference.
 *
 * It will have value in creation thread and null otherwise.
 */
@Deprecated(
    "Is obsolete in new memory model.",
    level = DeprecationLevel.ERROR
)
public fun <T : Any> threadLocal(value: T): ReadOnlyProperty<Any, T?> = error("Obsolete in new memory model")

/**
 * Allows creating thread safe lazy to use with Kotlin-Native.
 */
@Deprecated(
    "Is obsolete in new memory model.",
    level = DeprecationLevel.ERROR
)
public fun <T : Any> sharedLazy(
    function: () -> T
): ReadOnlyProperty<Any, T> = error("Obsolete in new memory model")
