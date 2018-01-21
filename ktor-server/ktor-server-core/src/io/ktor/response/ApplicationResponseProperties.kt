package io.ktor.response

import io.ktor.http.*
import java.time.*
import java.time.temporal.*

fun ApplicationResponse.contentType(value: ContentType) = contentType(value.toString())
fun ApplicationResponse.contentType(value: String) = headers.append(HttpHeaders.ContentType, value)
fun ApplicationResponse.header(name: String, value: String) = headers.append(name, value)
fun ApplicationResponse.header(name: String, value: Int) = headers.append(name, value.toString())
fun ApplicationResponse.header(name: String, value: Long) = headers.append(name, value.toString())
fun ApplicationResponse.header(name: String, date: Temporal) = headers.append(name, date.toHttpDateString())

fun ApplicationResponse.etag(value: String) = header(HttpHeaders.ETag, value)
fun ApplicationResponse.lastModified(dateTime: ZonedDateTime) = header(HttpHeaders.LastModified, dateTime)
fun ApplicationResponse.contentLength(length: Long) = header(HttpHeaders.ContentLength, length)
fun ApplicationResponse.cacheControl(value: CacheControl) = header(HttpHeaders.CacheControl, value.toString())
fun ApplicationResponse.expires(value: LocalDateTime) = header(HttpHeaders.Expires, value)

fun HeadersBuilder.cacheControl(value: CacheControl) = set(HttpHeaders.CacheControl, value.toString())

fun HeadersBuilder.contentRange(range: LongRange?, fullLength: Long? = null, unit: String = RangeUnits.Bytes.unitToken) {
    append(HttpHeaders.ContentRange, contentRangeHeaderValue(range, fullLength, unit))
}
fun ApplicationResponse.contentRange(range: LongRange?, fullLength: Long? = null, unit: RangeUnits) {
    contentRange(range, fullLength, unit.unitToken)
}

fun ApplicationResponse.contentRange(range: LongRange?, fullLength: Long? = null, unit: String = RangeUnits.Bytes.unitToken) {
    header(HttpHeaders.ContentRange, contentRangeHeaderValue(range, fullLength, unit))
}

