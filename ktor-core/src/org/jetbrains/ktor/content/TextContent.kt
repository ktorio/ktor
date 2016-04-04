package org.jetbrains.ktor.content

import org.jetbrains.ktor.http.*

data class TextContent(override val contentType: ContentType, val text: String) : HasContentType {
    override fun toString() = "TextContent[$contentType] \"${text.take(30)}\""
}
