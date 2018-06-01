package io.ktor.http.content

import io.ktor.http.*

/**
 * Implementation of the [OutgoingContent.ByteArrayContent] for sending array of bytes
 */
class ByteArrayContent(
    private val bytes: ByteArray,
    override val contentType: ContentType? = null,
    override val status: HttpStatusCode? = null
) : OutgoingContent.ByteArrayContent() {
    override val contentLength: Long get() = bytes.size.toLong()

    override fun bytes(): ByteArray = bytes
}
