package io.ktor.http.content

import io.ktor.http.*
import kotlinx.io.charsets.*
import kotlinx.io.core.*

class
TextContent(val text: String, override val contentType: ContentType, override val status: HttpStatusCode? = null) : OutgoingContent.ByteArrayContent() {
    private val bytes by lazy(LazyThreadSafetyMode.NONE) { text.toByteArray(contentType.charset() ?: Charsets.UTF_8) }

    override val contentLength: Long
        get() = bytes.size.toLong()

    override fun bytes(): ByteArray = bytes

    override fun toString() = "TextContent[$contentType] \"${text.take(30)}\""
}