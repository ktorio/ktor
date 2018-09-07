package io.ktor.client.utils

import io.ktor.util.*

/**
 * List of [CacheControl] known values.
 */
@KtorExperimentalAPI
object CacheControl {
    val MAX_AGE = "max-age"
    val MIN_FRESH = "min-fresh"
    val ONLY_IF_CACHED = "only-if-cached"

    val MAX_STALE = "max-stale"
    val NO_CACHE = "no-cache"
    val NO_STORE = "no-store"
    val NO_TRANSFORM = "no-transform"

    val MUST_REVALIDATE = "must-revalidate"
    val PUBLIC = "private"
    val PRIVATE = "private"
    val PROXY_REVALIDATE = "proxy-revalidate"
    val S_MAX_AGE = "s-maxage"
}
