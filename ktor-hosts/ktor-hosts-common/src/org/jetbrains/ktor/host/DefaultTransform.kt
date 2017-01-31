package org.jetbrains.ktor.host

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.content.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.pipeline.*
import org.jetbrains.ktor.transform.*
import org.jetbrains.ktor.util.*
import java.io.*

fun ApplicationTransform<PipelineContext<ResponseMessage>>.registerDefaultHandlers() {
    register<String> { value ->
        val responseContentType = call.response.headers[HttpHeaders.ContentType]?.let { ContentType.parse(it) }
        val contentType = responseContentType ?: ContentType.Text.Plain.withCharset(Charsets.UTF_8)
        TextContent(value, contentType, null)
    }

    register<ByteArray> { value -> ByteArrayContent(value)}

    register<HttpStatusContent> { value ->
        TextContent("<H1>${value.code}</H1>${value.message.escapeHTML()}",
                ContentType.Text.Html.withCharset(Charsets.UTF_8),
                value.code)
    }

    register<HttpStatusCode> { value -> HttpStatusCodeContent(value) }

    register<URIFileContent> { value ->
        if (value.uri.scheme == "file") {
            LocalFileContent(File(value.uri))
        } else value
    }
}

class HttpStatusCodeContent(private val value: HttpStatusCode) : FinalContent.NoContent() {
    override val status: HttpStatusCode
        get() = value

    override val headers: ValuesMap
        get() = ValuesMap.Empty
}
