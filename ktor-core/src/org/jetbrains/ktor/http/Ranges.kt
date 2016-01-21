package org.jetbrains.ktor.http

import org.jetbrains.ktor.application.*
import java.util.*

object RangeUnits {
    val Bytes = "bytes"
}

data class PartialContentRange(val unit: String = RangeUnits.Bytes, val ranges: List<ContentRange>) {
    init {
        require(ranges.isNotEmpty()) { "It should be at least one range" }
    }

    override fun toString(): String = ranges.joinToString(",", prefix = unit + "=")
}
data class PartialContentResponse(val unit: String, val range: LongRange?, val fullLength: Long?) {
    override fun toString() = buildString {
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
}

interface ContentRange {
    data class ClosedContentRange(val from: Long, val to: Long) : ContentRange {
        override fun toString() = "$from-$to"
    }
    data class ClosedStartRange(val from: Long) : ContentRange {
        override fun toString() = "$from-"
    }
    data class LastUnitsRange(val lastCount: Long) : ContentRange {
        override fun toString() = "-$lastCount"
    }
}

fun ApplicationRequest.ranges() = header(HttpHeaders.Range)?.let { rangesSpec -> parseRangesSpecifier(rangesSpec) }

fun parseRangesSpecifier(rangeSpec: String): PartialContentRange {
    val (unit, allRangesString) = rangeSpec.chomp("=")
    val allRanges = allRangesString.split(',').map {
        val (from, to) = it.chomp("-")
        when {
            from.isNotEmpty() && to.isNotEmpty() -> ContentRange.ClosedContentRange(from.toLong(), to.toLong())
            from.isNotEmpty() -> ContentRange.ClosedStartRange(from.toLong())
            to.isNotEmpty() -> ContentRange.LastUnitsRange(to.toLong())
            else -> throw IllegalArgumentException("Wrong range specification: $rangeSpec")
        }
    }

    return PartialContentRange(unit, allRanges)
}

fun List<ContentRange>.resolveRanges(contentLength: Long) = map<ContentRange, ContentRange.ClosedContentRange> {
    when (it) {
        is ContentRange.ClosedContentRange -> it
        is ContentRange.ClosedStartRange -> ContentRange.ClosedContentRange(it.from, contentLength - 1)
        is ContentRange.LastUnitsRange -> ContentRange.ClosedContentRange(contentLength - it.lastCount, contentLength - 1)
        else -> throw UnsupportedOperationException()
    }
}.filter { it.from >= 0 && it.to < contentLength && it.from <= it.to }

fun List<ContentRange.ClosedContentRange>.mergeRanges() = sortedBy { it.from }.fold(ArrayList<ContentRange.ClosedContentRange>(size)) { acc, range ->
    when {
        acc.isEmpty() -> acc.add(range)
        acc.last().to < range.from - 1 -> acc.add(range)
        else -> {
            val last = acc.last()
            acc[acc.lastIndex] = ContentRange.ClosedContentRange(last.from, Math.max(last.to, range.to))
        }
    }
    acc
}

private fun String.chomp(separator: String): Pair<String, String> {
    val idx = indexOf(separator)
    if (idx == -1) {
        throw IllegalArgumentException("No separator found")
    }

    return substring(0, idx) to substring(idx + 1)
}
