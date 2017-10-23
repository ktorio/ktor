package io.ktor.http

// RFC 2616 sec 14.35.1
data class RangesSpecifier(val unit: String = RangeUnits.Bytes.unitToken, val ranges: List<ContentRange>) {

    constructor(unit: RangeUnits, ranges: List<ContentRange>) : this(unit.unitToken, ranges)

    init {
        require(ranges.isNotEmpty()) { "It should be at least one range" }
    }

    fun isValid(rangeUnitPredicate: (String) -> Boolean = { it == RangeUnits.Bytes.unitToken }) = rangeUnitPredicate(unit) && ranges.none {
        when (it) {
            is ContentRange.Bounded -> it.from < 0 || it.to < it.from
            is ContentRange.TailFrom -> it.from < 0
            is ContentRange.Suffix -> it.lastCount < 0
        }
    }

    // TODO rangeMergeMaxGap
    fun merge(length: Long, maxRangeCount: Int = 50): List<LongRange> {
        if (ranges.size > maxRangeCount) {
            return mergeToSingle(length).toList()
        }

        return merge(length)
    }

    /**
     * Merges all overlapping and neighbours ranges. Currently gaps collapse is not supported but should be, one day.
     */
    fun merge(length: Long): List<LongRange> = ranges.toLongRanges(length).mergeRangesKeepOrder()

    fun mergeToSingle(length: Long): LongRange? {
        val mapped = ranges.toLongRanges(length)

        if (mapped.isEmpty()) {
            return null
        }

        val start = mapped.minBy { it.start }!!.start
        val endInclusive = mapped.maxBy { it.endInclusive }!!.endInclusive.coerceAtMost(length - 1)

        return start .. endInclusive
    }

    override fun toString(): String = ranges.joinToString(",", prefix = unit + "=")

    private fun <T> T?.toList(): List<T> = if (this == null) emptyList() else listOf(this)
}