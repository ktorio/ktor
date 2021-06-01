/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.routing

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.response.*
import io.ktor.utils.io.*
import java.io.*

/**
 * Sends a [message] as a response
 */
@OptIn(ExperimentalStdlibApi::class)
@JvmName("respondWithType")
public suspend inline fun <reified T : Any> RoutingCall.respond(message: T): Unit = call.respond(message)

/**
 * Sets [status] and sends a [message] as a response
 */
@OptIn(ExperimentalStdlibApi::class)
@JvmName("respondWithType")
public suspend inline fun <reified T : Any> RoutingCall.respond(status: HttpStatusCode, message: T): Unit =
    call.respond(status, message)

/**
 * Responds to a client with a `301 Moved Permanently` or `302 Found` redirect
 */
public suspend fun RoutingCall.respondRedirect(url: String, permanent: Boolean = false): Unit =
    call.respondRedirect(url, permanent)

/**
 * Responds to a client with a `301 Moved Permanently` or `302 Found` redirect.
 * Unlike the other [respondRedirect] it provides a way to build URL based on current call using [block] function
 */
public suspend inline fun RoutingCall.respondRedirect(permanent: Boolean = false, block: URLBuilder.() -> Unit): Unit =
    call.respondRedirect(permanent, block)

/**
 * Responds to a client with a plain text response, using specified [text]
 * @param contentType is an optional [ContentType], default is [ContentType.Text.Plain]
 * @param status is an optional [HttpStatusCode], default is [HttpStatusCode.OK]
 */
public suspend fun RoutingCall.respondText(
    text: String,
    contentType: ContentType? = null,
    status: HttpStatusCode? = null,
    configure: OutgoingContent.() -> Unit = {}
): Unit = call.respondText(text, contentType, status, configure)

/**
 * Responds to a client with a plain text response, using specified [provider] to build a text
 * @param contentType is an optional [ContentType], default is [ContentType.Text.Plain]
 * @param status is an optional [HttpStatusCode], default is [HttpStatusCode.OK]
 */
public suspend fun RoutingCall.respondText(
    contentType: ContentType? = null,
    status: HttpStatusCode? = null,
    provider: suspend () -> String
): Unit = call.respondText(contentType, status, provider)

/**
 * Responds to a client with a raw bytes response, using specified [provider] to build a byte array
 * @param contentType is an optional [ContentType], unspecified by default
 * @param status is an optional [HttpStatusCode], default is [HttpStatusCode.OK]
 */
public suspend fun RoutingCall.respondBytes(
    contentType: ContentType? = null,
    status: HttpStatusCode? = null,
    provider: suspend () -> ByteArray
): Unit = call.respondBytes(contentType, status, provider)

/**
 * Responds to a client with a raw bytes response, using specified [bytes]
 * @param contentType is an optional [ContentType], unspecified by default
 * @param status is an optional [HttpStatusCode], default is [HttpStatusCode.OK]
 */
public suspend fun RoutingCall.respondBytes(
    bytes: ByteArray,
    contentType: ContentType? = null,
    status: HttpStatusCode? = null,
    configure: OutgoingContent.() -> Unit = {}
): Unit = call.respondBytes(bytes, contentType, status)

/**
 * Responds to a client with a contents of a file with the name [fileName] in the [baseDir] folder
 */
public suspend fun RoutingCall.respondFile(
    baseDir: File,
    fileName: String,
    configure: OutgoingContent.() -> Unit = {}
): Unit = call.respondFile(baseDir, fileName, configure)

/**
 * Responds to a client with a contents of a [file]
 */
public suspend fun RoutingCall.respondFile(file: File, configure: OutgoingContent.() -> Unit = {}): Unit =
    call.respondFile(file, configure)

/**
 * Respond with text content writer.
 *
 * The [writer] parameter will be called later when engine is ready to produce content.
 * Provided [Writer] will be closed automatically.
 */
public suspend fun RoutingCall.respondTextWriter(
    contentType: ContentType? = null,
    status: HttpStatusCode? = null,
    writer: suspend Writer.() -> Unit
): Unit = call.respondTextWriter(contentType, status, writer)

/**
 * Respond with binary content producer.
 *
 * The [producer] parameter will be called later when engine is ready to produce content. You don't need to close it.
 * Provided [OutputStream] will be closed automatically.
 */
public suspend fun RoutingCall.respondOutputStream(
    contentType: ContentType? = null,
    status: HttpStatusCode? = null,
    producer: suspend OutputStream.() -> Unit
): Unit = call.respondOutputStream(contentType, status, producer)

/**
 * Respond with binary content producer.
 *
 * The [producer] parameter will be called later when engine is ready to produce content. You don't need to close it.
 * Provided [ByteWriteChannel] will be closed automatically.
 */
public suspend fun RoutingCall.respondBytesWriter(
    contentType: ContentType? = null,
    status: HttpStatusCode? = null,
    producer: suspend ByteWriteChannel.() -> Unit
): Unit = call.respondBytesWriter(contentType, status, producer)
