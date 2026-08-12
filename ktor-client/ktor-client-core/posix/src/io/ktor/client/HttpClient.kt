/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client

import io.ktor.client.engine.*
import io.ktor.utils.io.*

/**
 * Constructs an asynchronous [HttpClient] using optional [block] for configuring this client.
 *
 * The [HttpClientEngine] is selected from the registered [engines].
 * All registered engine factories are ranked by their priority and the one with the highest priority is used.
 * An exception is thrown if no engines are registered or if multiple engines share the same highest priority.
 *
 * See https://ktor.io/docs/http-client-engines.html
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.HttpClient)
 */
public actual fun HttpClient(
    block: HttpClientConfig<*>.() -> Unit
): HttpClient = HttpClient(FACTORY, block)

@OptIn(InternalAPI::class)
private val FACTORY = run {
    val entries = engines.entries()
    when (entries.size) {
        0 -> error(
            "Failed to find HTTP client engine implementation: consider adding client engine dependency. " +
                "See https://ktor.io/docs/http-client-engines.html"
        )

        1 -> entries.single().factory

        else -> {
            val maxPriority = entries.maxOf { it.priority }
            val topEngines = entries.filter { it.priority == maxPriority }
            if (topEngines.size > 1) {
                error(
                    "Multiple HTTP client engine implementations share the same highest priority ($maxPriority): " +
                        topEngines.joinToString { it.factory.toString() } +
                        ". Specify an engine explicitly or assign distinct priorities to resolve the conflict. " +
                        "See https://ktor.io/docs/http-client-engines.html"
                )
            }
            topEngines.single().factory
        }
    }
}
