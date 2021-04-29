/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.http

import java.time.*
import java.time.format.*
import java.time.temporal.*
import java.util.*

/**
 * Format as HTTP date (GMT)
 */
public fun Temporal.toHttpDateString(): String = httpDateFormat.format(this)

/**
 * Parse HTTP date to [ZonedDateTime]
 */
@Deprecated(
    "This will be removed in future releases. Using it in 2.0.0 will be an error.",
    ReplaceWith("ZonedDateTime.parse(this, httpDateFormat)", "java.time.ZonedDateTime")
)
@Suppress("unused")
public fun String.fromHttpDateString(): ZonedDateTime = ZonedDateTime.parse(this, httpDateFormat)

private val GreenwichMeanTime: ZoneId = ZoneId.of("GMT")

/**
 * Default HTTP date format
 */
public val httpDateFormat: DateTimeFormatter = DateTimeFormatter
    .ofPattern("EEE, dd MMM yyyy HH:mm:ss z")
    .withLocale(Locale.US)
    .withZone(GreenwichMeanTime)!!
