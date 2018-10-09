package io.ktor.util

/**
 * Length of this [LongRange]
 */
@Deprecated("Not supported anymore.")
val LongRange.length: Long
    get() = (endInclusive - start + 1).coerceAtLeast(0L)

/**
 * Returns `true` if [other] range is fully contained inside [this] range
 */
@InternalAPI
operator fun LongRange.contains(other: LongRange) = other.start >= start && other.endInclusive <= endInclusive
