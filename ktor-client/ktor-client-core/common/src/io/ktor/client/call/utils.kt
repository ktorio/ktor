/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.call

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.content.*

@Suppress("KDocMissingDocumentation")
public class UnsupportedContentTypeException(content: OutgoingContent) :
    IllegalStateException("Failed to write body: ${content::class}")

@Suppress("KDocMissingDocumentation", "UNUSED")
public class UnsupportedUpgradeProtocolException(
    url: Url
) : IllegalArgumentException("Unsupported upgrade protocol exception: $url")

/**
 * Constructs a [HttpClientCall] from this [HttpClient] and
 * with the specified HTTP request [builder].
 */
@Deprecated(
    "Unbound [HttpClientCall] is deprecated. Consider using [request(builder)] instead.",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("this.request(builder)", "io.ktor.client.statement.*")
)
@Suppress("UNUSED", "UNUSED_PARAMETER", "RedundantSuspendModifier")
public suspend fun HttpClient.call(builder: HttpRequestBuilder): HttpClientCall =
    error("Unbound [HttpClientCall] is deprecated. Consider using [request(builder)] instead.")

/**
 * Constructs a [HttpClientCall] from this [HttpClient],
 * an [url] and an optional [block] configuring a [HttpRequestBuilder].
 */
@Deprecated(
    "Unbound [HttpClientCall] is deprecated. Consider using [request(urlString, block)] instead.",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith(
        "this.request(urlString, block)",
        "io.ktor.client.statement.*"
    )
)
@Suppress("UNUSED", "UNUSED_PARAMETER", "RedundantSuspendModifier")
public suspend fun HttpClient.call(
    urlString: String,
    block: suspend HttpRequestBuilder.() -> Unit = {}
): HttpClientCall = error(
    "Unbound [HttpClientCall] is deprecated. Consider using [request(urlString, block)] instead."
)

/**
 * Constructs a [HttpClientCall] from this [HttpClient],
 * an [url] and an optional [block] configuring a [HttpRequestBuilder].
 */
@Deprecated(
    "Unbound [HttpClientCall] is deprecated. Consider using [request(url, block)] instead.",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("this.request(url, block)", "io.ktor.client.statement.*")
)
@Suppress("UNUSED", "UNUSED_PARAMETER", "RedundantSuspendModifier")
public suspend fun HttpClient.call(
    url: Url,
    block: suspend HttpRequestBuilder.() -> Unit = {}
): HttpClientCall = error(
    "Unbound [HttpClientCall] is deprecated. Consider using [request(url, block)] instead."
)
