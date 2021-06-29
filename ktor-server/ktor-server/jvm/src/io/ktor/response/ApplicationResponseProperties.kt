/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

@file:Suppress("unused")

package io.ktor.response

import io.ktor.http.*
import java.time.*
import java.time.temporal.*

/**
 * Append HTTP response header with string [value]
 */
public fun ApplicationResponse.header(name: String, value: String): Unit = headers.append(name, value)

/**
 * Append HTTP response header with integer numeric [value]
 */
public fun ApplicationResponse.header(name: String, value: Int): Unit = headers.append(name, value.toString())

/**
 * Append HTTP response header with long integer numeric [value]
 */
public fun ApplicationResponse.header(name: String, value: Long): Unit = headers.append(name, value.toString())

/**
 * Append HTTP response header with temporal [date] (date, time and so on)
 */
public fun ApplicationResponse.header(name: String, date: Temporal): Unit =
    headers.append(name, date.toHttpDateString())

/**
 * Append response `E-Tag` HTTP header [value]
 */
public fun ApplicationResponse.etag(value: String): Unit = header(HttpHeaders.ETag, value)

/**
 * Append response `Last-Modified` HTTP header value from [dateTime]
 */
public fun ApplicationResponse.lastModified(dateTime: ZonedDateTime): Unit = header(HttpHeaders.LastModified, dateTime)

/**
 * Append response `Cache-Control` HTTP header [value]
 */
public fun ApplicationResponse.cacheControl(value: CacheControl): Unit =
    header(HttpHeaders.CacheControl, value.toString())

/**
 * Append response `Expires` HTTP header [value]
 */
public fun ApplicationResponse.expires(value: LocalDateTime): Unit = header(HttpHeaders.Expires, value)

/**
 * Append `Cache-Control` HTTP header [value]
 */
public fun HeadersBuilder.cacheControl(value: CacheControl): Unit = set(HttpHeaders.CacheControl, value.toString())

/**
 * Append 'Content-Range` header with specified [range] and [fullLength]
 */
public fun HeadersBuilder.contentRange(
    range: LongRange?,
    fullLength: Long? = null,
    unit: String = RangeUnits.Bytes.unitToken
) {
    append(HttpHeaders.ContentRange, contentRangeHeaderValue(range, fullLength, unit))
}

/**
 * Append response `Content-Range` header with specified [range] and [fullLength]
 */
public fun ApplicationResponse.contentRange(
    range: LongRange?,
    fullLength: Long? = null,
    unit: RangeUnits
) {
    contentRange(range, fullLength, unit.unitToken)
}

/**
 * Append response `Content-Range` header with specified [range] and [fullLength]
 */
public fun ApplicationResponse.contentRange(
    range: LongRange?,
    fullLength: Long? = null,
    unit: String = RangeUnits.Bytes.unitToken
) {
    header(HttpHeaders.ContentRange, contentRangeHeaderValue(range, fullLength, unit))
}
