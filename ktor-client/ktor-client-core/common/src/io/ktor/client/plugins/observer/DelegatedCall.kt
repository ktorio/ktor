/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.plugins.observer

import io.ktor.client.call.*
import io.ktor.http.*
import io.ktor.utils.io.*

/**
 * Wrap existing [HttpClientCall] with new [content].
 *
 * **Warning:** The content of the returned call response is non-replayable, so it can be consumed only once.
 * Consider using `replaceResponse` instead.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.observer.wrapWithContent)
 */
@Deprecated(
    "Use 'replaceResponse' instead.",
    ReplaceWith(
        "replaceResponse { content }",
        "io.ktor.client.call.replaceResponse"
    )
)
public fun HttpClientCall.wrapWithContent(content: ByteReadChannel): HttpClientCall = replaceResponse { content }

/**
 * Wrap existing [HttpClientCall] with new content produced by the given [block].
 * The [block] will be called each time the response content is requested.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.observer.wrapWithContent)
 */
@Deprecated(
    "Use 'replaceResponse' instead.",
    ReplaceWith(
        "replaceResponse { block() }",
        "io.ktor.client.call.replaceResponse"
    )
)
public fun HttpClientCall.wrapWithContent(block: () -> ByteReadChannel): HttpClientCall = replaceResponse { block() }

/**
 * Wrap existing [HttpClientCall] with new response [content] and [headers].
 *
 * **Warning:** The content of the returned call response is non-replayable, so it can be consumed only once.
 * Consider using `replaceResponse` instead.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.observer.wrap)
 */
@Deprecated(
    "Use 'replaceResponse' instead.",
    ReplaceWith(
        "replaceResponse(headers) { content }",
        "io.ktor.client.call.replaceResponse"
    )
)
public fun HttpClientCall.wrap(content: ByteReadChannel, headers: Headers): HttpClientCall =
    replaceResponse(headers) { content }
