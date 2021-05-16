/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util

import kotlin.reflect.*

//TODO remove after removal of deprecations
internal expect val KClass<*>.starProjectedType: KType
//TODO make internal and copy it to server-host-common ?
public expect fun Throwable.initCause(cause: Throwable): Throwable
internal expect fun <T : Any> KClass<T>.isAssignableFrom(value: T): Boolean

internal expect fun CharArray.binarySearch(element: Char, fromIndex: Int = 0, toIndex: Int = size): Int

//copy from jvm impl
internal fun CharArray.binarySearchCommon(element: Char, fromIndex: Int, toIndex: Int): Int {
    require(fromIndex <= toIndex) { "fromIndex($fromIndex) > toIndex($toIndex)" }
    if (fromIndex < 0) {
        throw IndexOutOfBoundsException("Array index out of range: $fromIndex")
    }
    if (toIndex > size) {
        throw IndexOutOfBoundsException("Array index out of range: $toIndex")
    }

    var low = fromIndex
    var high = toIndex - 1
    while (low <= high) {
        val mid = low + high ushr 1
        val midVal = get(mid)
        if (midVal < element) low = mid + 1 else if (midVal > element) high = mid - 1 else return mid // key found
    }
    return -(low + 1) // key not found.
}
