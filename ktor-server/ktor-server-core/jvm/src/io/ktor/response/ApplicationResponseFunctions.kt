/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

@file:Suppress("unused")

package io.ktor.response

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.util.*
import io.ktor.utils.io.*
import java.io.*
import kotlin.reflect.*

/**
 * Sends a [message] as a response
 */
@OptIn(ExperimentalStdlibApi::class)
@JvmName("respondWithType")
public suspend inline fun <reified T : Any> ApplicationCall.respond(message: T) {
    if (message !is OutgoingContent && message !is String && message !is ByteArray) {
        try {
            // We need to wrap getting type in try catch because of https://youtrack.jetbrains.com/issue/KT-42913
            response.responseType = typeOf<T>()
        } catch (_: Throwable) {
        }
    }
    response.pipeline.execute(this, message as Any)
}

/**
 * Sends a [message] as a response
 */
@Deprecated(
    message = "This method doesn't save type of the response. This can lead to error in serialization",
    level = DeprecationLevel.HIDDEN
)
public suspend inline fun ApplicationCall.respond(message: Any) {
    response.pipeline.execute(this, message)
}

/**
 * Sets [status] and sends a [message] as a response
 */
@OptIn(ExperimentalStdlibApi::class)
@JvmName("respondWithType")
public suspend inline fun <reified T : Any> ApplicationCall.respond(status: HttpStatusCode, message: T) {
    response.status(status)
    respond(message)
}

/**
 * Sets [status] and sends a [message] as a response
 */
@Deprecated(
    message = "This method doesn't save type of the response. This can lead to error in serialization",
    level = DeprecationLevel.HIDDEN
)
public suspend inline fun ApplicationCall.respond(status: HttpStatusCode, message: Any) {
    response.status(status)
    respond(message)
}

/**
 * Responds to a client with a `301 Moved Permanently` or `302 Found` redirect
 */
public suspend fun ApplicationCall.respondRedirect(url: String, permanent: Boolean = false) {
    response.headers.append(HttpHeaders.Location, url)
    respond(if (permanent) HttpStatusCode.MovedPermanently else HttpStatusCode.Found)
}

/**
 * Responds to a client with a `301 Moved Permanently` or `302 Found` redirect.
 * Unlike the other [respondRedirect] it provides a way to build URL based on current call using [block] function
 */
public suspend inline fun ApplicationCall.respondRedirect(permanent: Boolean = false, block: URLBuilder.() -> Unit) {
    respondRedirect(url(block), permanent)
}

/**
 * Responds to a client with a plain text response, using specified [text]
 * @param contentType is an optional [ContentType], default is [ContentType.Text.Plain]
 * @param status is an optional [HttpStatusCode], default is [HttpStatusCode.OK]
 */
public suspend fun ApplicationCall.respondText(
    text: String,
    contentType: ContentType? = null,
    status: HttpStatusCode? = null,
    configure: OutgoingContent.() -> Unit = {}
) {
    val message = TextContent(text, defaultTextContentType(contentType), status).apply(configure)
    respond(message)
}

/**
 * Responds to a client with a plain text response, using specified [provider] to build a text
 * @param contentType is an optional [ContentType], default is [ContentType.Text.Plain]
 * @param status is an optional [HttpStatusCode], default is [HttpStatusCode.OK]
 */
public suspend fun ApplicationCall.respondText(
    contentType: ContentType? = null,
    status: HttpStatusCode? = null,
    provider: suspend () -> String
) {
    val message = TextContent(provider(), defaultTextContentType(contentType), status)
    respond(message)
}

/**
 * Responds to a client with a raw bytes response, using specified [provider] to build a byte array
 * @param contentType is an optional [ContentType], unspecified by default
 * @param status is an optional [HttpStatusCode], default is [HttpStatusCode.OK]
 */
public suspend fun ApplicationCall.respondBytes(
    contentType: ContentType? = null,
    status: HttpStatusCode? = null,
    provider: suspend () -> ByteArray
) {
    respond(ByteArrayContent(provider(), contentType, status))
}

/**
 * Responds to a client with a raw bytes response, using specified [bytes]
 * @param contentType is an optional [ContentType], unspecified by default
 * @param status is an optional [HttpStatusCode], default is [HttpStatusCode.OK]
 */
public suspend fun ApplicationCall.respondBytes(
    bytes: ByteArray,
    contentType: ContentType? = null,
    status: HttpStatusCode? = null,
    configure: OutgoingContent.() -> Unit = {}
) {
    respond(ByteArrayContent(bytes, contentType, status).apply(configure))
}

/**
 * Responds to a client with a contents of a file with the name [fileName] in the [baseDir] folder
 */
public suspend fun ApplicationCall.respondFile(
    baseDir: File,
    fileName: String,
    configure: OutgoingContent.() -> Unit = {}
) {
    val message = LocalFileContent(baseDir, fileName).apply(configure)
    respond(message)
}

/**
 * Responds to a client with a contents of a [file]
 */
public suspend fun ApplicationCall.respondFile(file: File, configure: OutgoingContent.() -> Unit = {}) {
    val message = LocalFileContent(file).apply(configure)
    respond(message)
}

/**
 * Respond with text content writer.
 *
 * The [writer] parameter will be called later when engine is ready to produce content.
 * Provided [Writer] will be closed automatically.
 */
public suspend fun ApplicationCall.respondTextWriter(
    contentType: ContentType? = null,
    status: HttpStatusCode? = null,
    writer: suspend Writer.() -> Unit
) {
    val message = WriterContent(writer, defaultTextContentType(contentType), status)
    respond(message)
}

/**
 * Respond with binary content producer.
 *
 * The [producer] parameter will be called later when engine is ready to produce content. You don't need to close it.
 * Provided [OutputStream] will be closed automatically.
 */
public suspend fun ApplicationCall.respondOutputStream(
    contentType: ContentType? = null,
    status: HttpStatusCode? = null,
    producer: suspend OutputStream.() -> Unit
) {
    val message = OutputStreamContent(producer, contentType ?: ContentType.Application.OctetStream, status)
    respond(message)
}

/**
 * Respond with binary content producer.
 *
 * The [producer] parameter will be called later when engine is ready to produce content. You don't need to close it.
 * Provided [ByteWriteChannel] will be closed automatically.
 */
public suspend fun ApplicationCall.respondBytesWriter(
    contentType: ContentType? = null,
    status: HttpStatusCode? = null,
    producer: suspend ByteWriteChannel.() -> Unit
) {
    respond(ChannelWriterContent(producer, contentType ?: ContentType.Application.OctetStream, status))
}

/**
 * Creates a default [ContentType] based on the given [contentType] and current call
 *
 * If [contentType] is null, it tries to fetch already set response header "Content-Type". If the header is not available
 * `text/plain` is used. If [contentType] is specified, it uses it
 *
 * Additionally, if charset is not set for either content type, it appends `; charset=UTF-8` to the content type.
 */
public fun ApplicationCall.defaultTextContentType(contentType: ContentType?): ContentType {
    val result = when (contentType) {
        null -> {
            val headersContentType = response.headers[HttpHeaders.ContentType]
            headersContentType?.let {
                try {
                    ContentType.parse(headersContentType)
                } catch (_: BadContentTypeFormatException) {
                    null
                }
            } ?: ContentType.Text.Plain
        }
        else -> contentType
    }

    return if (result.charset() == null) {
        result.withCharset(Charsets.UTF_8)
    } else {
        result
    }
}
