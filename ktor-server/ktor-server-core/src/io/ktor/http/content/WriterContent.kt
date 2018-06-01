package io.ktor.http.content

import io.ktor.cio.*
import io.ktor.http.*
import kotlinx.coroutines.experimental.io.*
import java.io.*

class WriterContent(private val body: suspend Writer.() -> Unit,
                    override val contentType: ContentType,
                    override val status: HttpStatusCode? = null) : OutgoingContent.WriteChannelContent() {

    override suspend fun writeTo(channel: ByteWriteChannel) {
        val charset = contentType.charset() ?: Charsets.UTF_8
        channel.writer(charset).use {
            it.body()
        }
    }
}



