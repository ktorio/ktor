package io.ktor.util

import java.time.*
import java.util.*

/**
 * Creates [LocalDateTime] from this [Date]
 */
fun Date.toLocalDateTime(): LocalDateTime = LocalDateTime.ofInstant(toInstant(), ZoneId.systemDefault())

/**
 * Creates [ZonedDateTime] from this [Date]
 */
fun Date.toZonedDateTime(): ZonedDateTime = ZonedDateTime.ofInstant(toInstant(), GreenwichMeanTime)

val GreenwichMeanTime = ZoneId.of("GMT")
