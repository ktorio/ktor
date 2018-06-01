package io.ktor.http

import java.net.*

actual fun encodeURLQueryComponent(part: String): String = URLEncoder.encode(part, Charsets.UTF_8.name())
