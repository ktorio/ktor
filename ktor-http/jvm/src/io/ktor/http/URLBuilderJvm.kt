package io.ktor.http

import java.net.*

/**
 * Construct [Url] from [String]
 */
operator fun Url.Companion.invoke(fullUrl: String): Url = URLBuilder().apply {
    takeFrom(URI(fullUrl))
}.build()
