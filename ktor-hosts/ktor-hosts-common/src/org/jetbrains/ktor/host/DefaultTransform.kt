package org.jetbrains.ktor.host

import org.jetbrains.ktor.content.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.response.*
import org.jetbrains.ktor.util.*
import java.io.*

fun ApplicationSendPipeline.installDefaultTransformations() {
    intercept(ApplicationSendPipeline.Transform) { value ->
        val transformed = when (value) {
            is String -> {
                val responseContentType = call.response.headers[HttpHeaders.ContentType]?.let { ContentType.parse(it) }
                val contentType = responseContentType ?: ContentType.Text.Plain.withCharset(Charsets.UTF_8)
                TextContent(value, contentType, null)
            }
            is ByteArray -> {
                ByteArrayContent(value)
            }
            is HttpStatusContent -> {
                TextContent("<H1>${value.code}</H1><P>${value.message.escapeHTML()}</P>",
                        ContentType.Text.Html.withCharset(Charsets.UTF_8),
                        value.code)
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
        if (transformed != null)
            proceedWith(transformed)
    }
}

