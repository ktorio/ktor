/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

@file:Suppress("unused")

package io.ktor.response

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.util.*
import io.ktor.util.reflect.*
import io.ktor.utils.io.*
import java.io.*
import kotlin.reflect.*

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
