package org.jetbrains.ktor.content

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.pipeline.*
import org.jetbrains.ktor.response.*
import org.jetbrains.ktor.util.*
import java.io.*

fun PipelineContext<Any, ApplicationCall>.transformDefaultContent(value: Any): FinalContent? = when (value) {
    is FinalContent -> value
    is String -> {
        val contentType = call.defaultTextContentType(null)
        TextContent(value, contentType, null)
    }
    is ByteArray -> {
        ByteArrayContent(value)
    }
    is HttpStatusContent -> {
        TextContent("<H1>${value.code}</H1><P>${value.message.escapeHTML()}</P>",
                ContentType.Text.Html.withCharset(Charsets.UTF_8), value.code)
    }
    is HttpStatusCode -> {
        HttpStatusCodeContent(value)
    }
    is URIFileContent -> {
        if (value.uri.scheme == "file")
            LocalFileContent(File(value.uri))
        else
            null
    }
    else -> null
}
