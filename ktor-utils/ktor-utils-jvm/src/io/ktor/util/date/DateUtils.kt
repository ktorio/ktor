package io.ktor.util.date

import java.time.*

/**
 * Convert [Instant] to [GMTDate]
 */
fun Instant.toGMTDate(): GMTDate = GMTDate(atZone(ZoneOffset.UTC).toEpochSecond() * 1000L)

/**
 * Convert [ZonedDateTime] to [GMTDate]
 */
fun ZonedDateTime.toGMTDate(): GMTDate = toInstant().toGMTDate()
