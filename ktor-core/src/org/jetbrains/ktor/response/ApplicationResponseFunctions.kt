package org.jetbrains.ktor.response

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.content.*
import org.jetbrains.ktor.http.*
import java.io.*

/**
 * Sends a [message] as a response
 */
suspend fun ApplicationCall.respond(message: Any) {
    sendPipeline.execute(this, message)
}

suspend fun ApplicationCall.respondRedirect(url: String, permanent: Boolean = false) {
    response.headers.append(HttpHeaders.Location, url)
    respond(if (permanent) HttpStatusCode.MovedPermanently else HttpStatusCode.Found)
}

suspend fun ApplicationCall.respondText(text: String, contentType: ContentType? = null, status: HttpStatusCode? = null) {
    respond(TextContent(text, defaultTextContentType(contentType), status))
}

suspend fun ApplicationCall.respondText(contentType: ContentType? = null, status: HttpStatusCode? = null, provider: suspend () -> String) {
    respond(TextContent(provider(), defaultTextContentType(contentType), status))
}

/**
 * Respond with content producer.
 *
 * The [writer] parameter will be called later when host is ready to produce content. You don't need to close it.
 */
suspend fun ApplicationCall.respondWrite(contentType: ContentType? = null, status: HttpStatusCode? = null, writer: suspend Writer.() -> Unit) {
    respond(WriterContent(writer, defaultTextContentType(contentType), status))
}

fun ApplicationCall.defaultTextContentType(contentType: ContentType?): ContentType {
    val headersContentType = response.headers[HttpHeaders.ContentType]
    val result = when (contentType) {
        null -> headersContentType?.let { ContentType.parse(headersContentType) } ?: ContentType.Text.Plain
        else -> contentType
    }

    return if (result.charset() == null)
        result.withCharset(Charsets.UTF_8)
    else
        result
}

