package org.jetbrains.ktor.content

import org.jetbrains.ktor.http.*

@Deprecated("")
interface HasContentType {
    val contentType: ContentType
}

@Deprecated("")
interface HasContentLength {
    val contentLength: Long
}

