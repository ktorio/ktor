package org.jetbrains.ktor.tests.http

import org.jetbrains.ktor.http.*
import org.junit.*
import kotlin.test.*

class RangesTest {
    @Test
    fun testParseClosed() {
        assertEquals(PartialContentRange(RangeUnits.Bytes, listOf(ContentRange.ClosedContentRange(0, 499))),
                parseRangesSpecifier("bytes=0-499"))
    }

    @Test
    fun testParseClosedSecond() {
        assertEquals(PartialContentRange(RangeUnits.Bytes, listOf(ContentRange.ClosedContentRange(500, 999))),
                parseRangesSpecifier("bytes=500-999"))
    }

    @Test
    fun testParseFinalBytes() {
        assertEquals(PartialContentRange(RangeUnits.Bytes, listOf(ContentRange.LastUnitsRange(500))),
                parseRangesSpecifier("bytes=-500"))
    }

    @Test
    fun testParseFirstAndLastByteOnly() {
        assertEquals(PartialContentRange(RangeUnits.Bytes, listOf(
                ContentRange.ClosedContentRange(0, 0),
                ContentRange.LastUnitsRange(1)
        )),
                parseRangesSpecifier("bytes=0-0,-1"))
    }

    @Test
    fun testParseBytesFrom() {
        assertEquals(PartialContentRange(RangeUnits.Bytes, listOf(ContentRange.ClosedStartRange(9500))),
                parseRangesSpecifier("bytes=9500-"))
    }

    @Test
    fun testResolveRanges() {
        val ranges = listOf(ContentRange.ClosedContentRange(0, 10),
                ContentRange.ClosedStartRange(11),
                ContentRange.LastUnitsRange(5))

        assertEquals(
                listOf(ContentRange.ClosedContentRange(0, 10),
                        ContentRange.ClosedContentRange(11, 14),
                        ContentRange.ClosedContentRange(10, 14)),
                ranges.resolveRanges(15)
        )
    }

    @Test
    fun testMergeRanges() {
        val ranges = listOf(ContentRange.ClosedContentRange(0, 10),
                ContentRange.ClosedContentRange(12, 13))

        assertEquals(listOf(ContentRange.ClosedContentRange(0, 10),
                ContentRange.ClosedContentRange(12, 13)), ranges.mergeRanges())
    }

    @Test
    fun testMergeRangesReverse() {
        val ranges = listOf(ContentRange.ClosedContentRange(12, 13), ContentRange.ClosedContentRange(0, 10))

        assertEquals(listOf(ContentRange.ClosedContentRange(0, 10),
                ContentRange.ClosedContentRange(12, 13)), ranges.mergeRanges())
    }

    @Test
    fun testMergeRangesJustOneByte() {
        val ranges = listOf(ContentRange.ClosedContentRange(500, 600),
                ContentRange.ClosedContentRange(601, 999))

        assertEquals(listOf(ContentRange.ClosedContentRange(500, 999)), ranges.mergeRanges())
    }

    @Test
    fun testMergeRangesIntersection() {
        val ranges = listOf(ContentRange.ClosedContentRange(500, 700),
                ContentRange.ClosedContentRange(601, 999))

        assertEquals(listOf(ContentRange.ClosedContentRange(500, 999)), ranges.mergeRanges())
    }

    @Test
    fun testMergeRangesIntersectionOneInsideAnother() {
        val ranges = listOf(ContentRange.ClosedContentRange(0, 100),
                ContentRange.ClosedContentRange(10, 50))

        assertEquals(listOf(ContentRange.ClosedContentRange(0, 100)), ranges.mergeRanges())
    }

    @Test
    fun testMergeRangesSameStart() {
        val ranges = listOf(ContentRange.ClosedContentRange(0, 10),
                ContentRange.ClosedContentRange(0, 50))

        assertEquals(listOf(ContentRange.ClosedContentRange(0, 50)), ranges.mergeRanges())
    }

    @Test
    fun testRenderRanges() {
        assertEquals("bytes=0-0,1-,-1", PartialContentRange(RangeUnits.Bytes, listOf(
                ContentRange.ClosedContentRange(0, 0),
                ContentRange.ClosedStartRange(1),
                ContentRange.LastUnitsRange(1)
        )).toString())
    }

    @Test
    fun testPartialContentResponseRender() {
        assertEquals("bytes 0-0/1", PartialContentResponse(RangeUnits.Bytes, 0L..0L, 1L).toString())
    }

    @Test
    fun testPartialContentResponseRenderUnknownSize() {
        assertEquals("bytes 0-0/*", PartialContentResponse(RangeUnits.Bytes, 0L..0L, null).toString())
    }

    @Test
    fun testPartialContentResponseRenderUnsatisfiableRange() {
        assertEquals("bytes */1", PartialContentResponse(RangeUnits.Bytes, null, 1L).toString())
    }

    @Test
    fun testPartialContentResponseRenderUnsatisfiableRangeUnknownSize() {
        assertEquals("bytes */*", PartialContentResponse(RangeUnits.Bytes, null, null).toString())
    }
}