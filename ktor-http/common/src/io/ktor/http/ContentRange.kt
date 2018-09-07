package io.ktor.http

import io.ktor.util.*

/**
 * Format `Content-Range` header value
 */
@KtorExperimentalAPI
fun contentRangeHeaderValue(
    range: LongRange?,
    fullLength: Long? = null,
    unit: RangeUnits = RangeUnits.Bytes
): String =
    contentRangeHeaderValue(range, fullLength, unit.unitToken)

/**
 * Format `Content-Range` header value
 */
@KtorExperimentalAPI
fun contentRangeHeaderValue(
    range: LongRange?,
    fullLength: Long? = null,
    unit: String = RangeUnits.Bytes.unitToken
): String = buildString {
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
