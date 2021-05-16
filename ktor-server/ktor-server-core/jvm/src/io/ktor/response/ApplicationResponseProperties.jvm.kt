/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

@file:Suppress("unused")

package io.ktor.response

import io.ktor.http.*
import java.time.*
import java.time.temporal.*

/**
 * Append HTTP response header with temporal [date] (date, time and so on)
 */
public fun ApplicationResponse.header(name: String, date: Temporal): Unit =
    headers.append(name, date.toHttpDateString())

/**
 * Append response `Last-Modified` HTTP header value from [dateTime]
 */
public fun ApplicationResponse.lastModified(dateTime: ZonedDateTime): Unit = header(HttpHeaders.LastModified, dateTime)

/**
 * Append response `Expires` HTTP header [value]
 */
public fun ApplicationResponse.expires(value: LocalDateTime): Unit = header(HttpHeaders.Expires, value)
