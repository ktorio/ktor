package org.jetbrains.ktor.content

import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.http.response.*
import org.jetbrains.ktor.response.*
import org.jetbrains.ktor.util.*
import java.time.*

/**
 * Designates a resource that can be mixed into [FinalContent] to give more information about content
 */
interface Resource {
    val contentType: ContentType
    val contentLength: Long?

    val versions: List<Version>
    val expires: LocalDateTime?
    val cacheControl: CacheControl?

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