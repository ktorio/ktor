package org.jetbrains.ktor.http

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
            else -> true
        }
    }

    fun merge(length: Long, mergeToSingle: Boolean = false): List<LongRange> {
        return if (mergeToSingle) {
            mergeToSingle(length)?.let { listOf(it) } ?: emptyList()
        } else {
            merge(length)
        }
    }

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
}