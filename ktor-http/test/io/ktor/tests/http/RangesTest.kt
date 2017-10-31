package io.ktor.tests.http

import io.ktor.http.*
import org.junit.Test
import kotlin.test.*

class RangesTest {
    @Test
    fun testParseClosed() {
        assertEquals(RangesSpecifier(RangeUnits.Bytes, listOf(ContentRange.Bounded(0, 499))),
                parseRangesSpecifier("bytes=0-499"))
    }

    @Test
    fun testParseClosedSecond() {
        assertEquals(RangesSpecifier(RangeUnits.Bytes, listOf(ContentRange.Bounded(500, 999))),
                parseRangesSpecifier("bytes=500-999"))
    }

    @Test
    fun testParseFinalBytes() {
        assertEquals(RangesSpecifier(RangeUnits.Bytes, listOf(ContentRange.Suffix(500))),
                parseRangesSpecifier("bytes=-500"))
    }

    @Test
    fun testParseFirstAndLastByteOnly() {
        assertEquals(RangesSpecifier(RangeUnits.Bytes, listOf(
                ContentRange.Bounded(0, 0),
                ContentRange.Suffix(1)
        )),
                parseRangesSpecifier("bytes=0-0,-1"))
    }

    @Test
    fun testParseBytesFrom() {
        assertEquals(RangesSpecifier(RangeUnits.Bytes, listOf(ContentRange.TailFrom(9500))),
                parseRangesSpecifier("bytes=9500-"))
    }

    @Test
    fun testParseEmpty() {
        assertNull(parseRangesSpecifier(""))
    }

    @Test
    fun testParseWrongUnit() {
        assertNull(parseRangesSpecifier("miles=10-100"))
    }

    @Test
    fun testParseWrongNoValues() {
        assertNull(parseRangesSpecifier("bytes=-"))
    }

    @Test
    fun testParseWrongNegativeEnd() {
        assertNull(parseRangesSpecifier("bytes=--1"))
        assertNull(parseRangesSpecifier("bytes=1--1"))
    }

    @Test
    fun testParseStartGreaterThanEnd() {
        assertNull(parseRangesSpecifier("bytes=10-1"))
    }

    @Test
    fun testParseWrongNoUnit() {
        assertNull(parseRangesSpecifier("=10-1"))
        assertNull(parseRangesSpecifier("10-1"))
    }

    @Test
    fun testParseWrongNegativeStart() {
        assertNull(parseRangesSpecifier("bytes=-10-1"))
    }

    @Test
    fun testResolveRanges() {
        val ranges = listOf(ContentRange.Bounded(0, 10),
                ContentRange.TailFrom(11),
                ContentRange.Suffix(5))

        assertEquals(
                listOf(0..10,
                        11..14,
                        10..14),
                ranges.toLongRanges(15)
        )
    }

    @Test
    fun testMergeRanges() {
        val ranges = longRanges(0 .. 10, 12 .. 13)
        assertEquals(longRanges(0 .. 10, 12 .. 13), ranges.mergeRangesKeepOrder())
    }

    @Test
    fun testMergeRangesReverse() {
        val ranges = longRanges(12 .. 13, 0 .. 10)
        assertEquals(longRanges(12 .. 13, 0 .. 10), ranges.mergeRangesKeepOrder())
    }

    @Test
    fun testMergeRangesJustOneByte() {
        val ranges = longRanges(500 .. 600, 601 .. 999)
        assertEquals(longRanges(500 .. 999), ranges.mergeRangesKeepOrder())
    }

    @Test
    fun testMergeRangesIntersection() {
        val ranges = longRanges(500 .. 700, 601 .. 999)
        assertEquals(longRanges(500 .. 999), ranges.mergeRangesKeepOrder())
    }

    @Test
    fun testMergeRangesIntersectionOneInsideAnother() {
        val ranges = longRanges(0 .. 100, 10 .. 50)

        assertEquals(longRanges(0 .. 100), ranges.mergeRangesKeepOrder())
    }

    @Test
    fun testMergeRangesSameStart() {
        val ranges = longRanges(0 .. 10, 0 .. 50)

        assertEquals(longRanges(0 .. 50), ranges.mergeRangesKeepOrder())
    }

    @Test
    fun testMergeRangesMultiple() {
        val ranges = longRanges(100 .. 200, 10 .. 15, 80 .. 99)
        assertEquals(longRanges(80..200, 10..15), ranges.mergeRangesKeepOrder())
    }

    @Test
    fun testMergeToSingle() {
        assertEquals(0L..100L, RangesSpecifier(RangeUnits.Bytes, listOf(
                ContentRange.Bounded(0, 0),
                ContentRange.Bounded(90, 100),
                ContentRange.Bounded(5, 7)
        )).mergeToSingle(1000))
    }

    @Test
    fun testMergeRangeSpecifier() {
        assertEquals(listOf(0L..99L), RangesSpecifier(RangeUnits.Bytes, listOf(
                ContentRange.Bounded(0, 0),
                ContentRange.Bounded(1, 90),
                ContentRange.Suffix(10)
        )).merge(100))

        assertEquals(listOf(0L..99L), RangesSpecifier(RangeUnits.Bytes, listOf(
                ContentRange.Bounded(0, 0),
                ContentRange.Bounded(1, 90),
                ContentRange.TailFrom(90)
        )).merge(100))
    }

    @Test
    fun testRenderRanges() {
        assertEquals("bytes=0-0,1-,-1", RangesSpecifier(RangeUnits.Bytes, listOf(
                ContentRange.Bounded(0, 0),
                ContentRange.TailFrom(1),
                ContentRange.Suffix(1)
        )).toString())
    }

    @Test
    fun testPartialContentResponseRender() {
        assertEquals("bytes 0-0/1", contentRangeHeaderValue(0L..0L, 1L, RangeUnits.Bytes))
    }

    @Test
    fun testPartialContentResponseRenderUnknownSize() {
        assertEquals("bytes 0-0/*", contentRangeHeaderValue(0L..0L, null, RangeUnits.Bytes))
    }

    @Test
    fun testPartialContentResponseRenderUnsatisfiableRange() {
        assertEquals("bytes */1", contentRangeHeaderValue(null, 1L, RangeUnits.Bytes))
    }

    @Test
    fun testPartialContentResponseRenderUnsatisfiableRangeUnknownSize() {
        assertEquals("bytes */*", contentRangeHeaderValue(null, null, RangeUnits.Bytes))
    }

    private fun assertEquals(expected: List<IntRange>, actual: List<LongRange>) {
        assertEquals(expected.map { it.toLong() }, actual)
    }

    private fun longRanges(vararg ranges: IntRange): List<LongRange> = ranges.map { it.toLong() }

    private fun IntRange.toLong() = start.toLong()..endInclusive.toLong()
}