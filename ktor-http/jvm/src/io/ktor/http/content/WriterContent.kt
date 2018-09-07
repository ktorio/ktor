package io.ktor.http.content

import io.ktor.http.*
import io.ktor.util.cio.*
import kotlinx.coroutines.io.*
import java.io.*

/**
 * Represents a content that is produced by [body] function
 */
class WriterContent(
    private val body: suspend Writer.() -> Unit,
    override val contentType: ContentType,
    override val status: HttpStatusCode? = null
) : OutgoingContent.WriteChannelContent() {

    override suspend fun writeTo(channel: ByteWriteChannel) {
        val charset = contentType.charset() ?: Charsets.UTF_8
        channel.writer(charset).use {
            it.body()
        }
    }
}
