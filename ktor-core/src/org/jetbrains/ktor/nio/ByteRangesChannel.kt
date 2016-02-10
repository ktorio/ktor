package org.jetbrains.ktor.nio

import org.jetbrains.ktor.http.*
import java.io.*
import java.nio.channels.*

class ByteRangesChannel(val ranges: List<FileWithRange>, val boundary: String, val contentType: String) : ChainAsyncByteChannel(ranges.build(boundary, contentType)) {
    class FileWithRange(val file: File, val range: LongRange)

    init {
        require(ranges.size > 1) { "It should at least 2 file ranges" }
    }

    companion object {
        private fun List<FileWithRange>.build(boundary: String, contentType: String): Sequence<() -> AsynchronousByteChannel> {
            return asSequence().flatMap { fileRange ->
                sequenceOf({
                    ByteArrayAsynchronousChannel(buildString {
                        append(boundary)
                        append("\r\n")

                        append(HttpHeaders.ContentType)
                        append(": ")
                        append(contentType)
                        append("\r\n")

                        append(HttpHeaders.ContentRange)
                        append(": ")
                        append(contentRangeHeaderValue(fileRange.range, fileRange.file.length(), RangeUnits.Bytes))
                        append("\r\n")

                        append("\r\n")
                    }.toByteArray(Charsets.ISO_8859_1))
                }, {
                    fileRange.file.asyncReadOnlyFileChannel(fileRange.range.start, fileRange.range.endInclusive)
                }, {
                    ByteArrayAsynchronousChannel(buildString {
                        append("\r\n")
                    }.toByteArray(Charsets.ISO_8859_1))
                })
            } + sequenceOf({
                ByteArrayAsynchronousChannel(boundary.toByteArray(Charsets.ISO_8859_1))
            })
        }
    }

}
