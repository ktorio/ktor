package io.ktor.util.date

import java.time.*
import java.util.concurrent.*

/**
 * Convert [Instant] to [GMTDate]
 */
fun Instant.toGMTDate(): GMTDate = GMTDate(TimeUnit.SECONDS.toMillis(atZone(ZoneOffset.UTC).toEpochSecond()))

/**
 * Convert [ZonedDateTime] to [GMTDate]
 */
fun ZonedDateTime.toGMTDate(): GMTDate = toInstant().toGMTDate()
