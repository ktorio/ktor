package org.jetbrains.ktor.content

import org.jetbrains.ktor.http.*

interface HasETag {
    fun etag(): String
}

interface HasLastModified {
    val lastModified: Long
}

interface HasContentType {
    val contentType: ContentType
}

interface HasContentLength {
    val contentLength: Long
}

