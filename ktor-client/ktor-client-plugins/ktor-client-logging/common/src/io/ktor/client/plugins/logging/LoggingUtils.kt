/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.plugins.logging

import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.*
import io.ktor.utils.io.charsets.*
import io.ktor.utils.io.core.*

internal fun Appendable.logHeaders(
    headers: Set<Map.Entry<String, List<String>>>,
    sanitizedHeaders: List<SanitizedHeader>
) {
    val sortedHeaders = headers.toList().sortedBy { it.key }

    sortedHeaders.forEach { (key, values) ->
        val placeholder = sanitizedHeaders.firstOrNull { it.predicate(key) }?.placeholder
        logHeader(key, placeholder ?: values.joinToString("; "))
    }
}

internal fun Appendable.logHeader(key: String, value: String) {
    appendLine("-> $key: $value")
}

internal fun logResponseHeader(
    log: StringBuilder,
    response: HttpResponse,
    level: LogLevel,
    sanitizedHeaders: List<SanitizedHeader>
) {
    with(log) {
        if (level.info) {
            appendLine("RESPONSE: ${response.status}")
            appendLine("METHOD: ${response.call.request.method}")
            appendLine("FROM: ${response.call.request.url}")
        }

        if (level.headers) {
            appendLine("COMMON HEADERS")
            logHeaders(response.headers.entries(), sanitizedHeaders)
        }
    }
}

internal suspend inline fun ByteReadChannel.tryReadText(charset: Charset): String? = try {
    readRemaining().readText(charset = charset)
} catch (cause: Throwable) {
    null
}

@OptIn(InternalAPI::class)
internal suspend fun HttpClientCallLogger.logResponseBody(response: HttpResponse) {
    val log = StringBuilder()
    try {
        log.appendResponseBody(response.contentType(), response.rawContent)
    } catch (_: Throwable) {
    } finally {
        logResponseBody(log.toString().trim())
    }
}

internal suspend fun StringBuilder.appendResponseBody(
    contentType: ContentType?,
    content: ByteReadChannel
) {
    appendLine("BODY Content-Type: $contentType")
    appendLine("BODY START")

    val message = content.tryReadText(contentType?.charset() ?: Charsets.UTF_8) ?: "[response body omitted]"
    appendLine(message)
    append("BODY END")
}
