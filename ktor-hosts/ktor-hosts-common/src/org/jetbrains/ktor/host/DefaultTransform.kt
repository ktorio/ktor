package org.jetbrains.ktor.host

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.content.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.pipeline.*
import org.jetbrains.ktor.util.*
import java.io.*

fun PipelineContext<Any>.defaultHandlers(call: ApplicationCall): FinalContent? {
    val value = subject
    when (value) {
        is String -> {
            val responseContentType = call.response.headers[HttpHeaders.ContentType]?.let { ContentType.parse(it) }
            val contentType = responseContentType ?: ContentType.Text.Plain.withCharset(Charsets.UTF_8)
            return TextContent(value, contentType, null)
        }
        is ByteArray -> {
            return ByteArrayContent(value)
        }
        is HttpStatusContent -> {
            return TextContent("<H1>${value.code}</H1><P>${value.message.escapeHTML()}</P>",
                    ContentType.Text.Html.withCharset(Charsets.UTF_8),
                    value.code)
        }
        is HttpStatusCode -> {
            return HttpStatusCodeContent(value)
        }
        is URIFileContent -> {
            if (value.uri.scheme == "file")
                return LocalFileContent(File(value.uri))
        }
    }
    return null
}

