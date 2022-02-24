/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.http

import io.ktor.util.date.*
import java.time.*
import java.time.format.*
import java.time.temporal.*

/**
 * Format epoch milliseconds as HTTP date (GMT)
 */
@Deprecated(
    "This will be removed in future releases.",
    ReplaceWith("GMTDate(this).toHttpDate()", "io.ktor.util.date.GMTDate")
)
public fun Long.toHttpDateString(): String = GMTDate(this).toHttpDate()

/**
 * Format as HTTP date (GMT)
 */
@Suppress("unused")
@Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
@JvmName("toHttpDateString")
public fun Temporal.toHttpDateString0(): String = toHttpDateString()

/**
 * Parse HTTP date to [ZonedDateTime]
 */
@Suppress("unused")
@Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
@JvmName("fromHttpDateString")
public fun String.fromHttpDateString0(): ZonedDateTime = ZonedDateTime.parse(this, httpDateFormat)

/**
 * Default HTTP date format
 */
@Suppress("unused")
@Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
public val httpDateFormat0: DateTimeFormatter
    @JvmName("getHttpDateFormat")
    get() = httpDateFormat
