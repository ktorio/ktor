/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.call

import io.ktor.client.*
import io.ktor.client.request.*
import java.net.*

/**
 * Constructs a [HttpClientCall] from this [HttpClient],
 * an [url] and an optional [block] configuring a [HttpRequestBuilder].
 */
@Deprecated(
    "Unbound [HttpClientCall] is deprecated. Consider using [request<HttpResponse>(url, block)] instead.",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("this.request<HttpResponse>(url, block)", "io.ktor.client.statement.*")
)
@Suppress("RedundantSuspendModifier", "unused", "UNUSED_PARAMETER")
suspend fun HttpClient.call(url: URL, block: HttpRequestBuilder.() -> Unit = {}): HttpClientCall =
    error("Unbound [HttpClientCall] is deprecated. Consider using [request<HttpResponse>(url, block)] instead.")
