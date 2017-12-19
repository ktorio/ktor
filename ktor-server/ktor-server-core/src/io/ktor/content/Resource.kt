package io.ktor.content

import io.ktor.http.*
import io.ktor.response.*
import io.ktor.util.*
import java.time.*

/**
 * Represents a resource that can be mixed into [OutgoingContent] to give more information about content.
 */
interface Resource {
    /**
     * Specifies [ContentType] for this resource.
     */
    val contentType: ContentType

    /**
     * Specifies content length in bytes for this resource.
     *
     * If omitted the resources will be sent as `Transfer-Encoding: chunked`
     */
    val contentLength: Long?

    /**
     * Provides list of [Version] objects which can be utilized by [ConditionalHeaders] and [PartialContent] features.
     */
    val versions: List<Version>

    /**
     * Specifies expiration time for this resource.
     */
    val expires: LocalDateTime?

    /**
     * Specifies caching policy for this resource
     */
    val cacheControl: CacheControl?

    /**
     * Provides default implementation of headers property for instance of [OutgoingContent]
     */
    val headers: ValuesMap
        get() = ValuesMap.build(true) {
            contentType(contentType)
            contentLength?.let { contentLength ->
                contentLength(contentLength)
            }
            versions.forEach { it.appendHeadersTo(this) }
            expires?.let { expires ->
                expires(expires)
            }
            cacheControl?.let { cacheControl ->
                cacheControl(cacheControl)
            }
        }
}