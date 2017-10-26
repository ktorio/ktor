package io.ktor.content

import io.ktor.cio.*
import io.ktor.http.*
import io.ktor.util.*
import java.io.*

class WriterContent(private val body: suspend Writer.() -> Unit, private val contentType: ContentType, override val status: HttpStatusCode? = null) : OutgoingContent.WriteChannelContent() {
    override val headers: ValuesMap
        get() = ValuesMap.build(true) { contentType(contentType) }

    override suspend fun writeTo(channel: WriteChannel) {
        val charset = contentType.charset() ?: Charsets.UTF_8
        val writer = channel.toOutputStream().writer(charset)
        writer.use {
            it.body()
        }
        channel.close()
    }
}



