package org.jetbrains.ktor.content

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.util.*
import java.nio.file.attribute.*
import java.time.*
import java.util.*

interface HasVersions {
    val versions: List<Version>

    val headers: ValuesMap
        get() = ValuesMap.build(true) {
            versions.forEach { it.render(this) }
        }
}

interface Version {
    fun render(response: ApplicationResponse)
    fun render(builder: ValuesMapBuilder)
}

data class LastModifiedVersion(val lastModified: LocalDateTime) : Version {
    constructor(lastModified: FileTime) : this(LocalDateTime.ofInstant(lastModified.toInstant(), ZoneId.systemDefault()))
    constructor(lastModified: Date) : this(lastModified.toLocalDateTime())

    override fun render(response: ApplicationResponse) {
        response.lastModified(lastModified.atZone(ZoneOffset.UTC))
    }

    override fun render(builder: ValuesMapBuilder) {
        builder.lastModified(lastModified.atZone(ZoneOffset.UTC))
    }
}
data class EntityTagVersion(val etag: String) : Version {
    override fun render(response: ApplicationResponse) {
        response.etag(etag)
    }

    override fun render(builder: ValuesMapBuilder) {
        builder.etag(etag)
    }
}

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
    class MaxAge(val maxAgeSeconds: Int, val proxyMaxAgeSeconds: Int? = null, val mustRevalidate: Boolean = false, val proxyRevalidate: Boolean = false, visibility: CacheControlVisibility? = null) : CacheControl(visibility) {
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

fun FinalContent.lastModifiedAndEtagVersions(): List<Version> {
    if (this is HasVersions) {
        return versions
    }

    val headers = headers
    return headers.getAll(HttpHeaders.LastModified).orEmpty().map { LastModifiedVersion(LocalDateTime.parse(it, httpDateFormat)) } +
            headers.getAll(HttpHeaders.ETag).orEmpty().map { EntityTagVersion(it) }
}
