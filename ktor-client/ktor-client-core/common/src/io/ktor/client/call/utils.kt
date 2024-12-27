/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.call

import io.ktor.http.*
import io.ktor.http.content.*

/**
 * Exception thrown when the engine does not support the content type of the HTTP request body.
 * For instance, some engines do not support upgrade requests.
 */
public class UnsupportedContentTypeException(content: OutgoingContent) :
    IllegalStateException("Failed to write body: ${content::class}")

@Suppress("KDocMissingDocumentation", "UNUSED")
@Deprecated(
    "This exception is deprecated, use UnsupportedContentTypeException instead.",
    replaceWith = ReplaceWith("UnsupportedContentTypeException(content)"),
    level = DeprecationLevel.WARNING
)
public class UnsupportedUpgradeProtocolException(
    url: Url
) : IllegalArgumentException("Unsupported upgrade protocol exception: $url")

internal fun checkContentLength(contentLength: Long?, bodySize: Long, method: HttpMethod) {
    if (contentLength == null || contentLength < 0 || method == HttpMethod.Head) return

    if (contentLength != bodySize) {
        throw IllegalStateException(
            "Content-Length mismatch: expected $contentLength bytes, but received $bodySize bytes"
        )
    }
}
