/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:Suppress("unused")

package io.ktor.response

import io.ktor.http.*
import java.time.*
import java.time.temporal.*

/**
 * Append HTTP response header with string [value]
 */
fun ApplicationResponse.header(name: String, value: String, safeOnly: Boolean = true): Unit = headers.append(name, value, safeOnly)

/**
 * Append HTTP response header with integer numeric [value]
 */
fun ApplicationResponse.header(name: String, value: Int, safeOnly: Boolean = true): Unit = header(name, value.toString(), safeOnly)

/**
 * Append HTTP response header with long integer numeric [value]
 */
fun ApplicationResponse.header(name: String, value: Long, safeOnly: Boolean = true): Unit = header(name, value.toString(), safeOnly)

/**
 * Append HTTP response header with temporal [date] (date, time and so on)
 */
fun ApplicationResponse.header(name: String, date: Temporal, safeOnly: Boolean = true): Unit = header(name, date.toHttpDateString(), safeOnly)

/**
 * Append response `E-Tag` HTTP header [value]
 */
fun ApplicationResponse.etag(value: String, safeOnly: Boolean = true): Unit = header(HttpHeaders.ETag, value, safeOnly)

/**
 * Append response `Last-Modified` HTTP header value from [dateTime]
 */
fun ApplicationResponse.lastModified(dateTime: ZonedDateTime, safeOnly: Boolean = true): Unit = header(HttpHeaders.LastModified, dateTime, safeOnly)

/**
 * Append response `Cache-Control` HTTP header [value]
 */
fun ApplicationResponse.cacheControl(value: CacheControl, safeOnly: Boolean = true): Unit = header(HttpHeaders.CacheControl, value.toString(), safeOnly)

/**
 * Append response `Expires` HTTP header [value]
 */
fun ApplicationResponse.expires(value: LocalDateTime, safeOnly: Boolean = true): Unit = header(HttpHeaders.Expires, value, safeOnly)

/**
 * Append `Cache-Control` HTTP header [value]
 */
fun HeadersBuilder.cacheControl(value: CacheControl): Unit = set(HttpHeaders.CacheControl, value.toString())

/**
 * Append 'Content-Range` header with specified [range] and [fullLength]
 */
fun HeadersBuilder.contentRange(
    range: LongRange?,
    fullLength: Long? = null,
    unit: String = RangeUnits.Bytes.unitToken
) {
    append(HttpHeaders.ContentRange, contentRangeHeaderValue(range, fullLength, unit))
}

/**
 * Append response `Content-Range` header with specified [range] and [fullLength]
 */
fun ApplicationResponse.contentRange(
    range: LongRange?,
    fullLength: Long? = null,
    unit: RangeUnits
) {
    contentRange(range, fullLength, unit.unitToken)
}

/**
 * Append response `Content-Range` header with specified [range] and [fullLength]
 */
fun ApplicationResponse.contentRange(
    range: LongRange?,
    fullLength: Long? = null,
    unit: String = RangeUnits.Bytes.unitToken
) {
    header(HttpHeaders.ContentRange, contentRangeHeaderValue(range, fullLength, unit))
}
