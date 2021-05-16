/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util

import kotlin.reflect.*
import kotlin.collections.binarySearch as binarySearchJvm
import kotlin.reflect.full.starProjectedType as starProjectedTypeJava

internal actual val KClass<*>.starProjectedType: KType
    get() = starProjectedTypeJava

@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
public actual fun Throwable.initCause(cause: Throwable): Throwable = this.initCause(cause)

internal actual fun <T : Any> KClass<T>.isAssignableFrom(value: T): Boolean =
    this.javaObjectType.isAssignableFrom(value.javaClass)

internal actual fun CharArray.binarySearch(
    element: Char,
    fromIndex: Int,
    toIndex: Int
): Int = binarySearchJvm(element, fromIndex, toIndex)
