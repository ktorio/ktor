package org.jetbrains.ktor.content

import org.jetbrains.ktor.util.*
import java.nio.file.attribute.*
import java.time.*
import java.util.*

interface HasVersions {
    val versions: List<Version>
}

interface Version

data class LastModifiedVersion(val lastModified: LocalDateTime) : Version {
    constructor(lastModified: FileTime) : this(LocalDateTime.ofInstant(lastModified.toInstant(), ZoneId.systemDefault()))
    constructor(lastModified: Date) : this(lastModified.toLocalDateTime())
}
data class EntityTagVersion(val etag: String) : Version

enum class CacheControlVisibility {
    PUBLIC,
    PRIVATE
}
sealed class CacheControl(val visibility: CacheControlVisibility?) {
    class NoCache(visibility: CacheControlVisibility?) : CacheControl(visibility) {
        override fun toString() = if (visibility == null) {
            "no-cache"
        } else {
            "no-cache, ${visibility.name.toLowerCase()}"
        }
    }
    class NoStore(visibility: CacheControlVisibility?) : CacheControl(visibility) {
        override fun toString() = if (visibility == null) {
            "no-store"
        } else {
            "no-store, ${visibility.name.toLowerCase()}"
        }
    }
    class MaxAge(val maxAgeSeconds: Int, val proxyMaxAgeSeconds: Int?, val mustRevalidate: Boolean, val proxyRevalidate: Boolean, visibility: CacheControlVisibility?) : CacheControl(visibility) {
        override fun toString(): String {
            val parts = ArrayList<String>(5)
            parts.add("max-age=$maxAgeSeconds")
            if (proxyMaxAgeSeconds != null) {
                parts.add("s-maxage=$proxyMaxAgeSeconds")
            }
            if (mustRevalidate) {
                parts.add("must-revalidate")
            }
            if (proxyRevalidate) {
                parts.add("proxy-revalidate")
            }
            if (visibility != null) {
                parts.add(visibility.name.toLowerCase())
            }

            return parts.joinToString(", ")
        }
    }
}
