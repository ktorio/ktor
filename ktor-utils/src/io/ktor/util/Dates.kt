package io.ktor.util

import java.time.*
import java.util.*

/**
 * Creates [ZonedDateTime] from this [Date]
 */
fun Date.toDateTime(): ZonedDateTime = ZonedDateTime.ofInstant(toInstant(), ZoneId.systemDefault())!!

/**
 * Creates [LocalDateTime] from this [Date]
 */
fun Date.toLocalDateTime(): LocalDateTime = LocalDateTime.ofInstant(toInstant(), ZoneId.systemDefault())

/**
 * Converts given [LocalDateTime] to GMT timezone
 */
fun LocalDateTime.toGMT(): ZonedDateTime = atZone(ZoneId.of("GMT"))

/**
 * Converts given [LocalDateTime] to GMT timezone
 */
fun ZonedDateTime.toGMT(): ZonedDateTime = toLocalDateTime().toGMT()
