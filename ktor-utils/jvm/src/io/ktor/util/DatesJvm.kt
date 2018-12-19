package io.ktor.util

import java.time.*
import java.util.*

/**
 * Creates [LocalDateTime] from this [Date]
 */
@Deprecated("Shouldn't be used outside of ktor")
fun Date.toLocalDateTime(): LocalDateTime = LocalDateTime.ofInstant(toInstant(), ZoneId.systemDefault())

/**
 * Creates [ZonedDateTime] from this [Date]
 */
@Suppress("DEPRECATION")
@Deprecated("Shouldn't be used outside of ktor")
fun Date.toZonedDateTime(): ZonedDateTime = ZonedDateTime.ofInstant(toInstant(), GreenwichMeanTime)

/**
 * [ZoneId] for GMT
 */
@Deprecated("Shouldn't be used outside of ktor")
val GreenwichMeanTime: ZoneId = ZoneId.of("GMT")
