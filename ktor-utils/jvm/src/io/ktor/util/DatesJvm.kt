package io.ktor.util

import java.time.*
import java.util.*

/**
 * Creates [LocalDateTime] from this [Date]
 */
@InternalAPI
fun Date.toLocalDateTime(): LocalDateTime = LocalDateTime.ofInstant(toInstant(), ZoneId.systemDefault())

/**
 * Creates [ZonedDateTime] from this [Date]
 */
@Suppress("DEPRECATION")
@InternalAPI
fun Date.toZonedDateTime(): ZonedDateTime = ZonedDateTime.ofInstant(toInstant(), GreenwichMeanTime)

/**
 * [ZoneId] for GMT
 */
@InternalAPI
val GreenwichMeanTime: ZoneId = ZoneId.of("GMT")
