package org.jetbrains.ktor.http

import java.net.*

fun String.decodeURL(): String = URLDecoder.decode(this, Charsets.UTF_8.name())
fun String.encodeURL(): String = URLEncoder.encode(this, Charsets.UTF_8.name())
fun String.unquoteAndUnescape() = when {
    startsWith('"') && endsWith('"') -> removeSurrounding("\"").replace("\\\\.".toRegex()) { it.value.takeLast(1) }
    else -> this
}
