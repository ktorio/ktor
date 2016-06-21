package org.jetbrains.ktor.transform

import org.jetbrains.ktor.content.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.nio.*
import org.jetbrains.ktor.util.*
import java.nio.charset.*

class TextContentResponse(override val status: HttpStatusCode?, contentType: ContentType?, text: String) : FinalContent.ChannelContent() {
    private val bytes by lazy {
        val encoding = contentType?.parameter("charset") ?: "UTF-8"
        text.toByteArray(Charset.forName(encoding))
    }

    override val headers by lazy {
        ValuesMap.build(true) {
            if (contentType != null) {
                contentType(contentType)
            }
            contentLength(bytes.size.toLong())
        }
    }

    override fun channel() = ByteArrayAsyncReadChannel(bytes)
}