/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client

import io.ktor.client.engine.*
import io.ktor.client.engine.js.*
import io.ktor.utils.io.*

/**
 * Constructs an asynchronous [HttpClient] using optional [block] for configuring this client.
 *
 * The [HttpClientEngine] is selected from the dependencies.
 * https://ktor.io/docs/http-client-engines.html
 */
@KtorDsl
public actual fun HttpClient(
    block: HttpClientConfig<*>.() -> Unit
): HttpClient = HttpClient(FACTORY, block)

// we need to fall back to the default (Js) engine if there are no other engines,
// but in the presence of other engines, they're preferred.
@OptIn(InternalAPI::class)
private val FACTORY = engines.firstOrNull { it != Js } ?: Js
