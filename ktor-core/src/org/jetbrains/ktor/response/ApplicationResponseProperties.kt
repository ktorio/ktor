package org.jetbrains.ktor.response

import org.jetbrains.ktor.content.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.util.*
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

fun ValuesMapBuilder.contentType(contentType: ContentType) = set(HttpHeaders.ContentType, contentType.toString())
fun ValuesMapBuilder.contentLength(length: Long) = set(HttpHeaders.ContentLength, length.toString())
fun ValuesMapBuilder.etag(entityTag: String) = set(HttpHeaders.ETag, entityTag)
fun ValuesMapBuilder.lastModified(dateTime: ZonedDateTime) = set(HttpHeaders.LastModified, dateTime.toHttpDateString())
fun ValuesMapBuilder.cacheControl(value: CacheControl) = set(HttpHeaders.CacheControl, value.toString())
fun ValuesMapBuilder.expires(expires: LocalDateTime) = set(HttpHeaders.Expires, expires.toHttpDateString())
fun ValuesMapBuilder.contentRange(range: LongRange?, fullLength: Long? = null, unit: String = RangeUnits.Bytes.unitToken) {
    append(HttpHeaders.ContentRange, contentRangeHeaderValue(range, fullLength, unit))
}