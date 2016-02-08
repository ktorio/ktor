package org.jetbrains.ktor.http

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.util.*
import java.util.*

object RangeUnits {
    val Bytes = "bytes"
}

// RFC 2616 sec 14.35.1
data class RangesSpecifier(val unit: String = RangeUnits.Bytes, val ranges: List<ContentRange>) {
    init {
        require(ranges.isNotEmpty()) { "It should be at least one range" }
    }

    fun isValid() = unit == RangeUnits.Bytes && ranges.none {
        when (it) {
            is ContentRange.Bounded -> it.from < 0 || it.to < it.from
            is ContentRange.TailFrom -> it.from < 0
            is ContentRange.Suffix -> it.lastCount < 0
            else -> true
        }
    }

    fun merge(length: Long): List<LongRange> = ranges.toLongRanges(length).mergeRangesKeepOrder()

    fun mergeToSingle(length: Long): LongRange {
        val mapped = ranges.toLongRanges(length)

        val start = mapped.minBy { it.start }!!.start
        val endInclusive = mapped.maxBy { it.endInclusive }!!.endInclusive.coerceAtMost(length - 1)

        return start .. endInclusive
    }

    override fun toString(): String = ranges.joinToString(",", prefix = unit + "=")
}

fun contentRangeHeaderValue(range: LongRange?, fullLength: Long? = null, unit: String = RangeUnits.Bytes) = buildString {
    append(unit)
    append(" ")
    if (range != null) {
        append(range.start)
        append('-')
        append(range.endInclusive)
    } else {
        append('*')
    }
    append('/')
    append(fullLength ?: "*")
}

fun ApplicationResponse.contentRange(range: LongRange?, fullLength: Long? = null, unit: String = RangeUnits.Bytes) {
    header(HttpHeaders.ContentRange, contentRangeHeaderValue(range, fullLength, unit))
}

interface ContentRange {
    data class Bounded(val from: Long, val to: Long) : ContentRange {
        override fun toString() = "$from-$to"
    }
    data class TailFrom(val from: Long) : ContentRange {
        override fun toString() = "$from-"
    }
    data class Suffix(val lastCount: Long) : ContentRange {
        override fun toString() = "-$lastCount"
    }
}

fun ApplicationRequest.ranges() = header(HttpHeaders.Range)?.let { rangesSpec -> parseRangesSpecifier(rangesSpec) }

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
        is ContentRange.Bounded -> it.from .. it.to.coerceAtMost(contentLength - 1)
        is ContentRange.TailFrom -> it.from .. contentLength - 1
        is ContentRange.Suffix -> (contentLength - it.lastCount).coerceAtLeast(0L) .. contentLength - 1
        else -> throw NoWhenBranchMatchedException("Unsupported ContentRange type ${it.javaClass}: $it")
    }
}

// O (N^2 + N ln (N) + N)
internal fun List<LongRange>.mergeRangesKeepOrder(): List<LongRange> {
    val sortedMerged = sortedBy { it.start }.fold(ArrayList<LongRange>(size)) { acc, range ->
        when {
            acc.isEmpty() -> acc.add(range)
            acc.last().endInclusive < range.start - 1 -> acc.add(range)
            else -> {
                val last = acc.last()
                acc[acc.lastIndex] = last.start .. Math.max(last.endInclusive, range.endInclusive)
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
