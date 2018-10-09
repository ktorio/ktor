package io.ktor.http.content

import io.ktor.util.cio.*
import io.ktor.http.*
import io.ktor.util.*
import java.net.*

/**
 * Represents a content that is served from the specified [uri]
 * @property uri that is used as a source
 */
@KtorExperimentalAPI
class URIFileContent(
    val uri: URI,
    override val contentType: ContentType = ContentType.defaultForFilePath(uri.path)
) : OutgoingContent.ReadChannelContent() {
    constructor(url: URL, contentType: ContentType = ContentType.defaultForFilePath(url.path)) : this(
        url.toURI(), contentType
    )

    override fun readFrom() = uri.toURL().openStream().toByteReadChannel() // TODO: use http client
}
