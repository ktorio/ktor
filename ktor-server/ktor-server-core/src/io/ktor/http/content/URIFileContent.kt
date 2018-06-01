package io.ktor.http.content

import io.ktor.cio.*
import io.ktor.http.*
import java.net.*

class URIFileContent(
        val uri: URI,
        override val contentType: ContentType = ContentType.defaultForFilePath(uri.path)
) : OutgoingContent.ReadChannelContent() {
    constructor(url: URL, contentType: ContentType = ContentType.defaultForFilePath(url.path)) : this(url.toURI(), contentType)

    override fun readFrom() = uri.toURL().openStream().toByteReadChannel() // TODO: use http client
}
