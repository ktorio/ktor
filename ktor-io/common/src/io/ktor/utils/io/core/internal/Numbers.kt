/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.utils.io.core.internal

@PublishedApi
@Suppress("NOTHING_TO_INLINE")
internal inline fun Long.toIntOrFail(name: String): Int {
    if (this >= Int.MAX_VALUE) failLongToIntConversion(this, name)
    return toInt()
}

@PublishedApi
internal fun failLongToIntConversion(value: Long, name: String): Nothing =
    throw IllegalArgumentException("Long value $value of $name doesn't fit into 32-bit integer")
