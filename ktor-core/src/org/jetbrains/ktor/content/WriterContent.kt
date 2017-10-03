package org.jetbrains.ktor.content

import org.jetbrains.ktor.cio.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.http.response.*
import org.jetbrains.ktor.util.*
import java.io.*

class WriterContent(private val body: suspend Writer.() -> Unit, private val contentType: ContentType, override val status: HttpStatusCode? = null) : FinalContent.WriteChannelContent() {
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



