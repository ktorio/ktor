package io.ktor.http.content

import io.ktor.http.*
import io.ktor.util.*
import io.ktor.util.date.*

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

