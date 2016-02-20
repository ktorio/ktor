package org.jetbrains.ktor.content

import org.jetbrains.ktor.http.*

@Deprecated("")
interface HasETag : HasVersion {
    fun etag(): String
}

@Deprecated("")
interface HasVersion {
}

@Deprecated("")
interface HasLastModified : HasVersion {
    val lastModified: Long
}

@Deprecated("")
interface HasContentType {
    val contentType: ContentType
}

@Deprecated("")
interface HasContentLength {
    val contentLength: Long
}

