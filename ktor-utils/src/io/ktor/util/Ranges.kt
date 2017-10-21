package io.ktor.util

val LongRange.length: Long
    get() = (endInclusive - start + 1).coerceAtLeast(0L)

operator fun LongRange.contains(other: LongRange) = other.start >= start && other.endInclusive <= endInclusive

fun List<LongRange>.isAscending(): Boolean = fold(true to 0L) { acc, e -> (acc.first && acc.second <= e.start) to e.start }.first