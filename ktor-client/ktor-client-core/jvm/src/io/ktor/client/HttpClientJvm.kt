/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client

import io.ktor.client.engine.*
import io.ktor.util.reflect.*
import io.ktor.utils.io.*
import java.util.*

/**
 * Constructs an asynchronous [HttpClient] using optional [block] for configuring this client.
 *
 * The [HttpClientEngine] is selected from the dependencies using [ServiceLoader].
 * All [HttpClientEngineContainer] implementations found on the classpath are ranked by [HttpClientEngineContainer.priority]
 * and the one with the highest priority is used. An exception is thrown if no implementations are found.
 *
 * See https://ktor.io/docs/http-client-engines.html
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.HttpClient)
 */
public actual fun HttpClient(
    block: HttpClientConfig<*>.() -> Unit
): HttpClient = HttpClient(FACTORY, block)

@OptIn(InternalAPI::class)
private val FACTORY: HttpClientEngineFactory<*> by lazy {
    selectDefaultHttpClientEngine(loadServices())
}
