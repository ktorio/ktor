package io.ktor.client.engine

import io.ktor.client.utils.*
import io.ktor.http.*
import io.ktor.http.content.*

/**
 * Merge headers from [content] and [requestHeaders] according to [OutgoingContent] properties
 */
fun mergeHeaders(
    requestHeaders: Headers,
    content: OutgoingContent,
    block: (key: String, value: String) -> Unit
) {
    buildHeaders {
        appendAll(requestHeaders)
        appendAll(content.headers)
    }.forEach { key, values ->
        if (HttpHeaders.ContentLength == key) return@forEach // set later
        if (HttpHeaders.ContentType == key) return@forEach // set later

        block(key, values.joinToString(";"))
    }

    val type = requestHeaders[HttpHeaders.ContentType] ?: content.contentType?.toString()
    val length = requestHeaders[HttpHeaders.ContentLength] ?: content.contentLength?.toString()

    type?.let { block(HttpHeaders.ContentType, it) }
    length?.let { block(HttpHeaders.ContentLength, it) }
}
