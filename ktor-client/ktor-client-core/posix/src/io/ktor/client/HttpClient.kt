/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client

import io.ktor.client.engine.*
import io.ktor.utils.io.*

/**
 * Constructs an asynchronous [HttpClient] using optional [block] for configuring this client.
 *
 * The [HttpClientEngine] is selected from the dependencies.
 * https://ktor.io/docs/http-client-engines.html
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.HttpClient)
 */
@KtorDsl
public actual fun HttpClient(
    block: HttpClientConfig<*>.() -> Unit
): HttpClient = HttpClient(FACTORY, block)

@OptIn(InternalAPI::class)
private val FACTORY = engines.firstOrNull() ?: error(
    "Failed to find HTTP client engine implementation: consider adding client engine dependency. " +
        "See https://ktor.io/docs/http-client-engines.html"
)
