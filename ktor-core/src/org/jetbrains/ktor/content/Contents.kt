package org.jetbrains.ktor.content

import org.jetbrains.ktor.http.*

interface HasETag : HasVersion {
    fun etag(): String
}

interface HasVersion {
}

interface HasLastModified : HasVersion {
    val lastModified: Long
}

interface HasContentType {
    val contentType: ContentType
}

interface HasContentLength {
    val contentLength: Long
}

