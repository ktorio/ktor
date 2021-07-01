/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.statement

import io.ktor.client.*
import io.ktor.http.*
import io.ktor.utils.io.*
import io.ktor.utils.io.charsets.*

/**
 * Executes this statement and run [HttpClient.responsePipeline] with the response and expected type [T].
 *
 * Note if T is a streaming type, you should manage how to close it manually.
 */
@Deprecated("Use `body` method instead", replaceWith = ReplaceWith("this.body<T>()"), level = DeprecationLevel.ERROR)
public suspend inline fun <reified T> HttpStatement.receive(): T = error("Use `body` method instead")

/**
 * Executes this statement and run the [block] with a [HttpClient.responsePipeline] execution result.
 *
 * Note that T can be a streamed type such as [ByteReadChannel].
 */
@Deprecated("Use `body` method instead", replaceWith = ReplaceWith("this.body<T>()"), level = DeprecationLevel.ERROR)
public suspend inline fun <reified T, R> HttpStatement.receive(crossinline block: suspend (response: T) -> R): R =
    error("Use `body` method instead")

/**
 * Read the [HttpResponse.content] as a String. You can pass an optional [charset]
 * to specify a charset in the case no one is specified as part of the Content-Type response.
 * If no charset specified either as parameter or as part of the response,
 * [io.ktor.client.plugins.HttpPlainText] settings will be used.
 *
 * Note that [fallbackCharset] parameter will be ignored if the response already has a charset.
 *      So it just acts as a fallback, honoring the server preference.
 */
@Deprecated(
    "Use `bodyAsText` method instead",
    replaceWith = ReplaceWith("this.bodyAsText()"),
    level = DeprecationLevel.ERROR
)
public suspend fun HttpResponse.readText(fallbackCharset: Charset? = null): String =
    error("Use `bodyAsText` method instead")
