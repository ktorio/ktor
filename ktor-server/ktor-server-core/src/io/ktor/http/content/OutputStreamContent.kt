package io.ktor.http.content

import io.ktor.http.*
import kotlinx.coroutines.experimental.io.*
import kotlinx.coroutines.experimental.io.jvm.javaio.*
import java.io.*

class OutputStreamContent(private val body: suspend OutputStream.() -> Unit,
                          override val contentType: ContentType,
                          override val status: HttpStatusCode? = null) : OutgoingContent.WriteChannelContent() {

    override suspend fun writeTo(channel: ByteWriteChannel) {
        channel.toOutputStream().use { it.body() }
    }
}