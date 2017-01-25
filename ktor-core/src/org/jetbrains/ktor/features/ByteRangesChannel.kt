package org.jetbrains.ktor.features

import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.cio.*
import org.jetbrains.ktor.util.*

object ByteRangesChannel {
    fun forSeekable(ranges: List<LongRange>, ch: RandomAccessReadChannel, fullLength: Long?, boundary: String, contentType: String): ReadChannel =
            CompositeReadChannel(ranges.build(fullLength, boundary, contentType) { range ->
                RangeReadChannel(ch, range.start, range.length)
            })

    fun forRegular(ranges: List<LongRange>, ch: ReadChannel, fullLength: Long?, boundary: String, contentType: String): ReadChannel {
        if (ch is RandomAccessReadChannel) {
            return forSeekable(ranges, ch, fullLength, boundary, contentType)
        }

        var position = 0L

        return CompositeReadChannel(ranges.build(fullLength, boundary, contentType) { range ->
            val start = position
            val skip = range.start - start
            position = range.endInclusive + 1

            RangeReadChannel(ch, skip, range.length)
        })
    }

    private fun List<LongRange>.build(fullLength: Long?, boundary: String, contentType: String, builder: (LongRange) -> ReadChannel): Sequence<() -> ReadChannel> {
        require(size > 1) { "There should be at least 2 file ranges" }

        return asSequence().flatMap { range ->
            sequenceOf({
                ByteBufferReadChannel(buildString {
                    append(boundary)
                    append("\r\n")

                    append(HttpHeaders.ContentType)
                    append(": ")
                    append(contentType)
                    append("\r\n")

                    append(HttpHeaders.ContentRange)
                    append(": ")
                    append(contentRangeHeaderValue(range, fullLength, RangeUnits.Bytes))
                    append("\r\n")

                    append("\r\n")
                }.toByteArray(Charsets.ISO_8859_1))
            }, {
                builder(range)
            }, {
                ByteBufferReadChannel(buildString {
                    append("\r\n")
                }.toByteArray(Charsets.ISO_8859_1))
            })
        } + sequenceOf({
            ByteBufferReadChannel(boundary.toByteArray(Charsets.ISO_8859_1))
        })
    }
}

