package io.ktor.util

import java.time.*
import java.util.*

internal fun Date.toDateTime() = ZonedDateTime.ofInstant(toInstant(), ZoneId.systemDefault())!!
internal fun Date.toLocalDateTime() = LocalDateTime.ofInstant(toInstant(), ZoneId.systemDefault())
internal fun LocalDateTime.toGMT() = atZone(ZoneId.of("GMT"))
internal fun ZonedDateTime.toGMT() = toLocalDateTime().toGMT()
