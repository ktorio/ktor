package io.ktor.http

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
