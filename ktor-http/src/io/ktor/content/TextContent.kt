package io.ktor.content

import io.ktor.http.*
import io.ktor.util.*

class TextContent(val text: String, val contentType: ContentType, override val status: HttpStatusCode? = null) : OutgoingContent.ByteArrayContent() {
    private val bytes by lazy(LazyThreadSafetyMode.NONE) { text.toByteArray(contentType.charset() ?: Charsets.UTF_8) }

    override val headers by lazy(LazyThreadSafetyMode.NONE) {
        ValuesMap.build(true) {
            contentType(contentType)
            contentLength(bytes.size.toLong())
        }
    }

    override fun bytes(): ByteArray = bytes

    override fun toString() = "TextContent[$contentType] \"${text.take(30)}\""
}