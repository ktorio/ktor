package io.ktor.http

import io.ktor.util.date.*

/**
 * Specifies caching properties for an [OutgoingContent] such as Cache-Control or Expires
 * @property cacheControl header
 * @property expires header
 */
data class CachingOptions(val cacheControl: CacheControl? = null, val expires: GMTDate? = null)
