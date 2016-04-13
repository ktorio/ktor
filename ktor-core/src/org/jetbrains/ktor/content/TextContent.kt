package org.jetbrains.ktor.content

import org.jetbrains.ktor.http.*

data class TextContent(val contentType: ContentType, val text: String) {
    override fun toString() = "TextContent[$contentType] \"${text.take(30)}\""
}
