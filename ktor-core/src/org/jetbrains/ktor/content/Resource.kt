package org.jetbrains.ktor.content

import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.response.*
import org.jetbrains.ktor.util.*
import java.time.*

interface Resource : HasVersions {
    val contentType: ContentType
    override val versions: List<Version>
    val expires: LocalDateTime?
    val cacheControl: CacheControl?
    @Deprecated("Shouldn't it be somewhere else instead?")
    val attributes: Attributes
    val contentLength: Long?

    override val headers: ValuesMap
        get() = ValuesMap.build(true) {
            appendAll(super.headers)
            contentType(contentType)
            expires?.let { expires ->
                expires(expires)
            }
            cacheControl?.let { cacheControl ->
                cacheControl(cacheControl)
            }
            contentLength?.let { contentLength ->
                contentLength(contentLength)
            }
        }
}