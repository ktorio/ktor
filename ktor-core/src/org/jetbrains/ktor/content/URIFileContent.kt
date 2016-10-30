package org.jetbrains.ktor.content

import org.jetbrains.ktor.http.*
import java.net.*

class URIFileContent(val uri: URI, override val contentType: ContentType = defaultContentType(uri.path.extension())): FinalContent.StreamContentProvider(), Resource {
    constructor(url: URL, contentType: ContentType = defaultContentType(url.path.extension())) : this(url.toURI(), contentType)

    override val headers by lazy { super.headers }

    override fun stream() = uri.toURL().openStream() // TODO: use http client

    override val versions: List<Version>
        get() = emptyList()

    override val expires = null
    override val cacheControl = null
    override val contentLength = null
}

private fun String.extension() = split("/\\").last().substringAfter(".")