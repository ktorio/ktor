package org.jetbrains.ktor.content

import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.response.*
import org.jetbrains.ktor.util.*
import java.nio.file.attribute.*
import java.time.*
import java.util.*

interface Version {
    fun appendHeadersTo(builder: ValuesMapBuilder)
}

data class LastModifiedVersion(val lastModified: LocalDateTime) : Version {
    constructor(lastModified: FileTime) : this(LocalDateTime.ofInstant(lastModified.toInstant(), ZoneId.systemDefault()))
    constructor(lastModified: Date) : this(lastModified.toLocalDateTime())

    override fun appendHeadersTo(builder: ValuesMapBuilder) {
        builder.lastModified(lastModified.atZone(ZoneOffset.UTC))
    }
}

data class EntityTagVersion(val etag: String) : Version {
    override fun appendHeadersTo(builder: ValuesMapBuilder) {
        builder.etag(etag)
    }
}

fun FinalContent.lastModifiedAndEtagVersions(): List<Version> {
    if (this is Resource) {
        return versions
    }

    val headers = headers
    val lastModifiedHeaders = headers.getAll(HttpHeaders.LastModified) ?: emptyList()
    val etagHeaders = headers.getAll(HttpHeaders.ETag) ?: emptyList()
    val versions = ArrayList<Version>(lastModifiedHeaders.size + etagHeaders.size)
    lastModifiedHeaders.mapTo(versions) { LastModifiedVersion(LocalDateTime.parse(it, httpDateFormat)) }
    etagHeaders.mapTo(versions) { EntityTagVersion(it) }
    return versions
}
