package org.jetbrains.ktor.util

internal val LongRange.length: Long
    get() = (endInclusive - start + 1).coerceAtLeast(0L)

internal operator fun LongRange.contains(other: LongRange) = other.start >= start && other.endInclusive <= endInclusive
