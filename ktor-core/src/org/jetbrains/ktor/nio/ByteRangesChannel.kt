package org.jetbrains.ktor.nio

import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.util.*

class ByteRangesChannel  {

    companion object {
        fun forSeekable(ranges: List<LongRange>, ch: SeekableAsyncChannel, fullLength: Long?, boundary: String, contentType: String) =
            ChainAsyncByteChannel(ranges.build(fullLength, boundary, contentType) { range ->
                AsyncSeekAndCut(ch, range.start, range.length, preventClose = true)
            })

        fun forRegular(ranges: List<LongRange>, ch: AsyncReadChannel, fullLength: Long?, boundary: String, contentType: String): ChainAsyncByteChannel {
            if (ch is SeekableAsyncChannel) {
                return forSeekable(ranges, ch, fullLength, boundary, contentType)
            }

            var position = 0L

            return ChainAsyncByteChannel(ranges.build(fullLength, boundary, contentType) { range ->
                val start = position
                val skip = range.start - start
                position = range.endInclusive + 1

                AsyncSkipAndCut(ch, skip, range.length, preventClose = true)
            })
        }

        private fun List<LongRange>.build(fullLength: Long?, boundary: String, contentType: String, builder: (LongRange) -> AsyncReadChannel): Sequence<() -> AsyncReadChannel> {
            require(size > 1) { "There should be at least 2 file ranges" }

            return asSequence().flatMap { range ->
                sequenceOf({
                    ByteArrayAsyncReadChannel(buildString {
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
                    ByteArrayAsyncReadChannel(buildString {
                        append("\r\n")
                    }.toByteArray(Charsets.ISO_8859_1))
                })
            } + sequenceOf({
                ByteArrayAsyncReadChannel(boundary.toByteArray(Charsets.ISO_8859_1))
            })
        }
    }

}
