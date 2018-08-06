package io.ktor.http

import io.ktor.util.*
import kotlin.math.*

enum class RangeUnits {
    Bytes,
    None;

    val unitToken = name.toLowerCase()
}

sealed class ContentRange {
    data class Bounded(val from: Long, val to: Long) : ContentRange() {
        override fun toString() = "$from-$to"
    }

    data class TailFrom(val from: Long) : ContentRange() {
        override fun toString() = "$from-"
    }

    data class Suffix(val lastCount: Long) : ContentRange() {
        override fun toString() = "-$lastCount"
    }
}

fun parseRangesSpecifier(rangeSpec: String): RangesSpecifier? {
    try {
        val (unit, allRangesString) = rangeSpec.chomp("=") { return null }
        val allRanges = allRangesString.split(',').map {
            if (it.startsWith("-")) {
                ContentRange.Suffix(it.removePrefix("-").toLong())
            } else {
                val (from, to) = it.chomp("-") { "" to "" }
                when {
                    to.isNotEmpty() -> ContentRange.Bounded(from.toLong(), to.toLong())
                    else -> ContentRange.TailFrom(from.toLong())
                }
            }
        }

        if (allRanges.isEmpty() || unit.isEmpty()) {
            return null
        }

        val spec = RangesSpecifier(unit, allRanges)
        return if (spec.isValid()) spec else null
    } catch (e: Throwable) {
        return null // according to the specification we should ignore syntactically incorrect headers
    }
}

internal fun List<ContentRange>.toLongRanges(contentLength: Long) = map {
    when (it) {
        is ContentRange.Bounded -> it.from..it.to.coerceAtMost(contentLength - 1)
        is ContentRange.TailFrom -> it.from until contentLength
        is ContentRange.Suffix -> (contentLength - it.lastCount).coerceAtLeast(0L) until contentLength
    }
}.filterNot { it.isEmpty() }

// O (N^2 + N ln (N) + N)
internal fun List<LongRange>.mergeRangesKeepOrder(): List<LongRange> {
    val sortedMerged = sortedBy { it.start }.fold(ArrayList<LongRange>(size)) { acc, range ->
        when {
            acc.isEmpty() -> acc.add(range)
            acc.last().endInclusive < range.start - 1 -> acc.add(range)
            else -> {
                val last = acc.last()
                acc[acc.lastIndex] = last.start..max(last.endInclusive, range.endInclusive)
            }
        }
        acc
    }
    val result = arrayOfNulls<LongRange>(size)

    for (range in sortedMerged) {
        for (i in indices) {
            if (this[i] in range) {
                result[i] = range
                break
            }
        }
    }

    return result.filterNotNull()
}
