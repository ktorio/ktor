package io.ktor.response

import io.ktor.application.*
import io.ktor.http.content.*
import io.ktor.http.*
import io.ktor.util.*
import java.io.*

/**
 * Sends a [message] as a response
 */
@Suppress("NOTHING_TO_INLINE")
suspend inline fun ApplicationCall.respond(message: Any) {
    response.pipeline.execute(this, message)
}

/**
 * Sets [status] and sends a [message] as a response
 */
@Suppress("NOTHING_TO_INLINE")
suspend inline fun ApplicationCall.respond(status: HttpStatusCode, message: Any) {
    response.status(status)
    response.pipeline.execute(this, message)
}

/**
 * Responds to a client with a `301 Moved Permanently` or `302 Found` redirect
 */
suspend fun ApplicationCall.respondRedirect(url: String, permanent: Boolean = false) {
    response.headers.append(HttpHeaders.Location, url)
    return respond(if (permanent) HttpStatusCode.MovedPermanently else HttpStatusCode.Found)
}

/**
 * Responds to a client with a `301 Moved Permanently` or `302 Found` redirect.
 * Unlike the other [respondRedirect] it provides a way to build URL based on current call using [block] function
 */
suspend inline fun ApplicationCall.respondRedirect(permanent: Boolean = false, block: URLBuilder.() -> Unit) {
    return respondRedirect(url(block), permanent)
}

/**
 * Responds to a client with a plain text response, using specified [text]
 * @param contentType is an optional [ContentType], default is [ContentType.Text.Plain]
 * @param status is an optional [HttpStatusCode], default is [HttpStatusCode.OK]
 */
suspend fun ApplicationCall.respondText(text: String, contentType: ContentType? = null, status: HttpStatusCode? = null, configure: OutgoingContent.() -> Unit = {}) {
    val message = TextContent(text, defaultTextContentType(contentType), status).apply(configure)
    return respond(message)
}

/**
 * Responds to a client with a plain text response, using specified [provider] to build a text
 * @param contentType is an optional [ContentType], default is [ContentType.Text.Plain]
 * @param status is an optional [HttpStatusCode], default is [HttpStatusCode.OK]
 */
suspend fun ApplicationCall.respondText(contentType: ContentType? = null, status: HttpStatusCode? = null, provider: suspend () -> String) {
    val message = TextContent(provider(), defaultTextContentType(contentType), status)
    return respond(message)
}

/**
 * Responds to a client with a raw bytes response, using specified [provider] to build a byte array
 * @param contentType is an optional [ContentType], unspecified by default
 * @param status is an optional [HttpStatusCode], default is [HttpStatusCode.OK]
 */
suspend fun ApplicationCall.respondBytes(contentType: ContentType? = null, status: HttpStatusCode? = null, provider: suspend () -> ByteArray) {
    return respond(ByteArrayContent(provider(), contentType, status))
}

/**
 * Responds to a client with a raw bytes response, using specified [bytes]
 * @param contentType is an optional [ContentType], unspecified by default
 * @param status is an optional [HttpStatusCode], default is [HttpStatusCode.OK]
 */
suspend fun ApplicationCall.respondBytes(bytes: ByteArray, contentType: ContentType? = null, status: HttpStatusCode? = null, configure: OutgoingContent.() -> Unit = {}) {
    return respond(ByteArrayContent(bytes, contentType, status).apply(configure))
}

/**
 * Responds to a client with a contents of a file with the name [fileName] in the [baseDir] folder
 */
suspend fun ApplicationCall.respondFile(baseDir: File, fileName: String, configure: OutgoingContent.() -> Unit = {}) {
    val message = LocalFileContent(baseDir, fileName).apply(configure)
    return respond(message)
}

/**
 * Responds to a client with a contents of a [file]
 */
suspend fun ApplicationCall.respondFile(file: File, configure: OutgoingContent.() -> Unit = {}) {
    val message = LocalFileContent(file).apply(configure)
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

/**
 * Creates a default [ContentType] based on the given [contentType] and current call
 *
 * If [contentType] is null, it tries to fetch already set response header "Content-Type". If the header is not available
 * `text/plain` is used. If [contentType] is specified, it uses it
 *
 * Additionally, if charset is not set for either content type, it appends `; charset=UTF-8` to the content type.
 */
fun ApplicationCall.defaultTextContentType(contentType: ContentType?): ContentType {
    val result = when (contentType) {
        null -> {
            val headersContentType = response.headers[HttpHeaders.ContentType]
            headersContentType?.let { ContentType.parse(headersContentType) } ?: ContentType.Text.Plain
        }
        else -> contentType
    }

    return if (result.charset() == null)
        result.withCharset(Charsets.UTF_8)
    else
        result
}

