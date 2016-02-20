package org.jetbrains.ktor.nio

import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.util.*
import java.io.*
import java.nio.channels.*

class ByteRangesChannel(val file: ChannelWithRange, val ranges: List<LongRange>, val boundary: String, val contentType: String) : ChainAsyncByteChannel(ranges.build(file, boundary, contentType)) {
    @Deprecated("")
    class FileWithRange(val file: File, val range: LongRange)

    class ChannelWithRange(val fc: AsynchronousFileChannel, val range: LongRange)

    init {
        require(ranges.size > 1) { "It should at least 2 file ranges" }
    }

    override fun close() {
        super.close()
        file.fc.close()
    }

    companion object {
        private fun List<LongRange>.build(file: ChannelWithRange, boundary: String, contentType: String): Sequence<() -> AsynchronousByteChannel> {
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
                        append(contentRangeHeaderValue(fileRange, file.range.length, RangeUnits.Bytes))
                        append("\r\n")

                        append("\r\n")
                    }.toByteArray(Charsets.ISO_8859_1))
                }, {
                    StatefulAsyncFileChannel(file.fc, fileRange.subRange(fileRange), preventClose = true)
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
