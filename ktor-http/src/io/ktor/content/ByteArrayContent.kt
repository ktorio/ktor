package io.ktor.content

/**
 * Implementation of the [OutgoingContent.ByteArrayContent] for sending array of bytes
 */
class ByteArrayContent(private val bytes: ByteArray) : OutgoingContent.ByteArrayContent() {
    override fun bytes(): ByteArray = bytes
}
