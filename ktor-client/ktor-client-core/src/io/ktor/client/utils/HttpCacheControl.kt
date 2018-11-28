package io.ktor.client.utils

import io.ktor.util.*

/**
 * List of [CacheControl] known values.
 */
@KtorExperimentalAPI
object CacheControl {
    const val MAX_AGE = "max-age"
    const val MIN_FRESH = "min-fresh"
    const val ONLY_IF_CACHED = "only-if-cached"

    const val MAX_STALE = "max-stale"
    const val NO_CACHE = "no-cache"
    const val NO_STORE = "no-store"
    const val NO_TRANSFORM = "no-transform"

    const val MUST_REVALIDATE = "must-revalidate"
    const val PUBLIC = "private"
    const val PRIVATE = "private"
    const val PROXY_REVALIDATE = "proxy-revalidate"
    const val S_MAX_AGE = "s-maxage"
}
