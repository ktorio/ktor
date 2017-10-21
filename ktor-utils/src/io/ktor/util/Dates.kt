package io.ktor.util

import java.time.*
import java.util.*

fun Date.toDateTime() = ZonedDateTime.ofInstant(toInstant(), ZoneId.systemDefault())!!
fun Date.toLocalDateTime() = LocalDateTime.ofInstant(toInstant(), ZoneId.systemDefault())
fun LocalDateTime.toGMT() = atZone(ZoneId.of("GMT"))
fun ZonedDateTime.toGMT() = toLocalDateTime().toGMT()
