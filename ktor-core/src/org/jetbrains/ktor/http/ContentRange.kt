package org.jetbrains.ktor.http

import org.jetbrains.ktor.response.*

fun contentRangeHeaderValue(range: LongRange?, fullLength: Long? = null, unit: RangeUnits = RangeUnits.Bytes) =
    contentRangeHeaderValue(range, fullLength, unit.unitToken)

fun contentRangeHeaderValue(range: LongRange?, fullLength: Long? = null, unit: String = RangeUnits.Bytes.unitToken) = buildString {
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

fun ApplicationResponse.contentRange(range: LongRange?, fullLength: Long? = null, unit: RangeUnits) {
    contentRange(range, fullLength, unit.unitToken)
}

fun ApplicationResponse.contentRange(range: LongRange?, fullLength: Long? = null, unit: String = RangeUnits.Bytes.unitToken) {
    header(HttpHeaders.ContentRange, contentRangeHeaderValue(range, fullLength, unit))
}
