package io.ktor.util

import io.ktor.util.date.*
import java.time.*

fun Instant.toGMTDate(): GMTDate = GMTDate(atZone(ZoneOffset.UTC).toEpochSecond() * 1000L)

fun ZonedDateTime.toGMTDate() = toInstant().toGMTDate()
