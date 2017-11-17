package io.ktor.response

import io.ktor.application.*
import io.ktor.content.*
import io.ktor.http.*
import java.io.*

/**
 * Sends a [message] as a response
 */
inline suspend fun ApplicationCall.respond(message: Any) {
    response.pipeline.execute(this, message)
}

/**
 * Sets [status] and sends a [message] as a response
 */
inline suspend fun ApplicationCall.respond(status: HttpStatusCode, message: Any) {
    response.status(status)
    response.pipeline.execute(this, message)
}

suspend fun ApplicationCall.respondRedirect(url: String, permanent: Boolean = false) {
    response.headers.append(HttpHeaders.Location, url)
    return respond(if (permanent) HttpStatusCode.MovedPermanently else HttpStatusCode.Found)
}

suspend fun ApplicationCall.respondText(text: String, contentType: ContentType? = null, status: HttpStatusCode? = null) {
    val message = TextContent(text, defaultTextContentType(contentType), status)
    return respond(message)
}

suspend fun ApplicationCall.respondFile(baseDir: File, fileName: String) {
    val message = LocalFileContent(baseDir, fileName)
    return respond(message)
}

suspend fun ApplicationCall.respondFile(file: File) {
    val message = LocalFileContent(file)
    return respond(message)
}

suspend fun ApplicationCall.respondText(contentType: ContentType? = null, status: HttpStatusCode? = null, provider: suspend () -> String) {
    val message = TextContent(provider(), defaultTextContentType(contentType), status)
    return respond(message)
}

/**
 * Respond with content producer.
 *
 * The [writer] parameter will be called later when engine is ready to produce content. You don't need to close it.
 */
suspend fun ApplicationCall.respondWrite(contentType: ContentType? = null, status: HttpStatusCode? = null, writer: suspend Writer.() -> Unit) {
    val message = WriterContent(writer, defaultTextContentType(contentType), status)
    return respond(message)
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

