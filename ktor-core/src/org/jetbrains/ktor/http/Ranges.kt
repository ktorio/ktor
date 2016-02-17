package org.jetbrains.ktor.http

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.content.*
import org.jetbrains.ktor.util.*
import java.util.*

enum class RangeUnits {
    Bytes,
    None;

    val unitToken = name.toLowerCase()
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

fun ApplicationCall.handleRangeRequest(version: HasVersion, length: Long, mergeToSingleRange: Boolean = false, block: (List<LongRange>?) -> Unit) {
    withIfRange(version) { range ->
        response.headers.append(HttpHeaders.AcceptRanges, RangeUnits.Bytes.unitToken)
        val merged = range?.merge(length, mergeToSingleRange)?.let {
            if (it.size > 10) {
                range.merge(length, true)
            } else it
        }

        if (request.httpMethod == HttpMethod.Head) {
            response.contentLength(length)
            respondStatus(HttpStatusCode.OK)
        } else if (request.httpMethod != HttpMethod.Get && merged != null) {
            respondStatus(HttpStatusCode.MethodNotAllowed, "Only GET and HEAD methods allowed for range requests")
        } else if (merged != null && merged.isEmpty()) {
            response.contentRange(range = null, fullLength = length) // https://tools.ietf.org/html/rfc7233#section-4.4
            respondStatus(HttpStatusCode.RequestedRangeNotSatisfiable, "No satisfiable ranges of $range")
        } else {
            block(merged)
        }
    }
}

internal fun List<ContentRange>.toLongRanges(contentLength: Long) = map {
    when (it) {
        is ContentRange.Bounded -> it.from..it.to.coerceAtMost(contentLength - 1)
        is ContentRange.TailFrom -> it.from..contentLength - 1
        is ContentRange.Suffix -> (contentLength - it.lastCount).coerceAtLeast(0L)..contentLength - 1
        else -> throw NoWhenBranchMatchedException("Unsupported ContentRange type ${it.javaClass}: $it")
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
                acc[acc.lastIndex] = last.start..Math.max(last.endInclusive, range.endInclusive)
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
