package io.ktor.http.content

import io.ktor.http.*
import io.ktor.util.*
import java.time.*

/**
 * Specifies a key for CacheControl extension property for [OutgoingContent]
 */
val CachingProperty = AttributeKey<CachingOptions>("Caching")

/**
 * Gets or sets [CacheControl] instance as an extension property on this content
 */
var OutgoingContent.caching: CachingOptions?
    get() = getProperty(CachingProperty)
    set(value) = setProperty(CachingProperty, value)

/**
 * Specifies caching properties for an [OutgoingContent] such as Cache-Control or Expires
 */
data class CachingOptions(val cacheControl: CacheControl? = null, val expires: ZonedDateTime? = null)

