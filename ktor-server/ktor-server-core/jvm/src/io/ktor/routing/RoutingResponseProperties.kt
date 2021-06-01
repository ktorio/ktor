/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

@file:Suppress("unused")

package io.ktor.routing

import io.ktor.http.*
import io.ktor.response.*
import java.time.*
import java.time.temporal.*

/**
 * Append HTTP response header with string [value]
 */
public fun RoutingResponse.header(name: String, value: String): Unit = call.response.header(name, value)

/**
 * Append HTTP response header with integer numeric [value]
 */
public fun RoutingResponse.header(name: String, value: Int): Unit = call.response.header(name, value)

/**
 * Append HTTP response header with long integer numeric [value]
 */
public fun RoutingResponse.header(name: String, value: Long): Unit = call.response.header(name, value)

/**
 * Append HTTP response header with temporal [date] (date, time and so on)
 */
public fun RoutingResponse.header(name: String, date: Temporal): Unit = call.response.header(name, date)

/**
 * Append response `E-Tag` HTTP header [value]
 */
public fun RoutingResponse.etag(value: String): Unit = call.response.etag(value)

/**
 * Append response `Last-Modified` HTTP header value from [dateTime]
 */
public fun RoutingResponse.lastModified(dateTime: ZonedDateTime): Unit = call.response.lastModified(dateTime)

/**
 * Append response `Cache-Control` HTTP header [value]
 */
public fun RoutingResponse.cacheControl(value: CacheControl): Unit = call.response.cacheControl(value)

/**
 * Append response `Expires` HTTP header [value]
 */
public fun RoutingResponse.expires(value: LocalDateTime): Unit = call.response.expires(value)

/**
 * Append response `Content-Range` header with specified [range] and [fullLength]
 */
public fun RoutingResponse.contentRange(range: LongRange?, fullLength: Long? = null, unit: RangeUnits): Unit =
    call.response.contentRange(range, fullLength, unit)

/**
 * Append response `Content-Range` header with specified [range] and [fullLength]
 */
public fun RoutingResponse.contentRange(
    range: LongRange?,
    fullLength: Long? = null,
    unit: String = RangeUnits.Bytes.unitToken
): Unit = call.response.contentRange(range, fullLength, unit)
