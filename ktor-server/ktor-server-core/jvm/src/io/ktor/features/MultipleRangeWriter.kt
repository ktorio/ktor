/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.features

import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*

private val NEWLINE = "\r\n".toByteArray(Charsets.ISO_8859_1)
private val FIXED_HEADERS_PART_LENGTH = 14 + HttpHeaders.ContentLength.length + HttpHeaders.ContentRange.length

/**
 * Start multirange response writer coroutine
 */
@Deprecated("This is going to be removed. Use PartialContent feature instead.")
public fun CoroutineScope.writeMultipleRanges(
    channelProducer: (LongRange) -> ByteReadChannel,
    ranges: List<LongRange>,
    fullLength: Long?,
    boundary: String,
    contentType: String
): ByteReadChannel {
    return writeMultipleRangesImpl(channelProducer, ranges, fullLength, boundary, contentType)
}

/**
 * Start multirange response writer coroutine
 */
internal fun CoroutineScope.writeMultipleRangesImpl(
    channelProducer: (LongRange) -> ByteReadChannel,
    ranges: List<LongRange>,
    fullLength: Long?,
    boundary: String,
    contentType: String
): ByteReadChannel = writer(Dispatchers.Unconfined, autoFlush = true) {
    for (range in ranges) {
        val current = channelProducer(range)
        channel.writeHeaders(range, boundary, contentType, fullLength)
        current.copyTo(channel)
        channel.writeFully(NEWLINE)
    }

    channel.writeFully("--$boundary--".toByteArray(Charsets.ISO_8859_1))
    channel.writeFully(NEWLINE)
}.channel

private suspend fun ByteWriteChannel.writeHeaders(
    range: LongRange,
    boundary: String,
    contentType: String,
    fullLength: Long?
) {
    val contentRangeHeaderValue = contentRangeHeaderValue(range, fullLength, RangeUnits.Bytes)
    val estimate = boundary.length + contentType.length + contentRangeHeaderValue.length + FIXED_HEADERS_PART_LENGTH
    val headers = buildString(estimate) {
        append("--")
        append(boundary)
        append("\r\n")

        append(HttpHeaders.ContentType)
        append(": ")
        append(contentType)
        append("\r\n")

        append(HttpHeaders.ContentRange)
        append(": ")
        append(contentRangeHeaderValue)
        append("\r\n")

        append("\r\n")
    }.toByteArray(Charsets.ISO_8859_1)

    writeFully(headers)
}
