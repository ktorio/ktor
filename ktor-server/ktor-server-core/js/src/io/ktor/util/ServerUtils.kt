/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util

import kotlin.reflect.*

internal actual val KClass<*>.starProjectedType: KType
    get() = error("Using KClass isn't supported, use function with KType instead")

@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
public actual fun Throwable.initCause(cause: Throwable): Throwable = this

internal actual fun <T : Any> KClass<T>.isAssignableFrom(value: T): Boolean = this.isInstance(value)

internal actual fun CharArray.binarySearch(
    element: Char,
    fromIndex: Int,
    toIndex: Int
): Int = binarySearchCommon(element, fromIndex, toIndex)
