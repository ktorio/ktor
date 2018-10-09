package io.ktor.http

import io.ktor.util.*
import java.time.*
import java.time.format.*
import java.time.temporal.*
import java.util.*

/**
 * Format epoch milliseconds as HTTP date (GMT)
 */
@KtorExperimentalAPI
fun Long.toHttpDateString(): String = Instant.ofEpochMilli(this).toHttpDateString()

/**
 * Format as HTTP date (GMT)
 */
fun Temporal.toHttpDateString(): String = httpDateFormat.format(this)

/**
 * Parse HTTP date to [ZonedDateTime]
 */
@KtorExperimentalAPI
fun String.fromHttpDateString(): ZonedDateTime = ZonedDateTime.parse(this, httpDateFormat)

/**
 * Default HTTP date format
 */
@KtorExperimentalAPI
val httpDateFormat = DateTimeFormatter
    .ofPattern("EEE, dd MMM yyyy HH:mm:ss z")
    .withLocale(Locale.US)
    .withZone(GreenwichMeanTime)!!
