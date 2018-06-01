package io.ktor.http.content

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.pipeline.*
import io.ktor.response.*
import java.io.*

fun PipelineContext<Any, ApplicationCall>.transformDefaultContent(value: Any): OutgoingContent? = when (value) {
    is OutgoingContent -> value
    is String -> {
        val contentType = call.defaultTextContentType(null)
        TextContent(value, contentType, null)
    }
    is ByteArray -> {
        ByteArrayContent(value)
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
