package io.ktor.http.content

import io.ktor.http.*
import kotlinx.coroutines.io.*
import kotlinx.coroutines.io.jvm.javaio.*
import java.io.*

/**
 * [OutgoingContent] to respond with [OutputStream].
 * The stream would be automatically closed after [body] finish.
 */
class OutputStreamContent(
    private val body: suspend OutputStream.() -> Unit,
    override val contentType: ContentType,
    override val status: HttpStatusCode? = null
) : OutgoingContent.WriteChannelContent() {

    override suspend fun writeTo(channel: ByteWriteChannel) {
        channel.toOutputStream().use { it.body() }
    }
}