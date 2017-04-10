package org.jetbrains.ktor.content

import org.jetbrains.ktor.cio.*
import org.jetbrains.ktor.http.*
import java.net.*

class URIFileContent(val uri: URI, override val contentType: ContentType = ContentType.defaultForFilePath(uri.path)): FinalContent.ReadChannelContent(), Resource {
    constructor(url: URL, contentType: ContentType = ContentType.defaultForFilePath(url.path)) : this(url.toURI(), contentType)

    override val headers by lazy { super<Resource>.headers }

    override fun readFrom() = uri.toURL().openStream().toReadChannel() // TODO: use http client

    override val versions: List<Version>
        get() = emptyList()

    override val expires = null
    override val cacheControl = null
    override val contentLength = null
}
