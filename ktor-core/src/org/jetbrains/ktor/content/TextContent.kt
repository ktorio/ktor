package org.jetbrains.ktor.content

import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.nio.*
import org.jetbrains.ktor.response.*
import org.jetbrains.ktor.util.*

data class TextContent(val contentType: ContentType, val text: String) {
    override fun toString() = "TextContent[$contentType] \"${text.take(30)}\""
}

class TextContentResponse(override val status: HttpStatusCode?, contentType: ContentType?, text: String) : FinalContent.ChannelContent() {
    private val bytes by lazy {
        text.toByteArray(contentType?.charset() ?: Charsets.UTF_8)
    }

    override val headers by lazy {
        ValuesMap.build(true) {
            if (contentType != null) {
                contentType(contentType)
            }
            contentLength(bytes.size.toLong())
        }
    }

    override fun channel() = ByteArrayReadChannel(bytes)
}