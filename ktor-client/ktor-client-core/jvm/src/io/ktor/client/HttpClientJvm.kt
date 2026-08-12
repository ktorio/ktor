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
 * and the one with the highest priority is used. An exception is thrown if no implementations are found
 * or if multiple implementations share the same highest priority.
 *
 * See https://ktor.io/docs/http-client-engines.html
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.HttpClient)
 */
public actual fun HttpClient(
    block: HttpClientConfig<*>.() -> Unit
): HttpClient = HttpClient(FACTORY, block)

/**
 * A container is searched across dependencies using [ServiceLoader] to find client implementations.
 * An implementation of this interface provides HTTP client [factory] and only used
 * to find the default client engine
 * when [HttpClient] function is called with no particular client implementation specified
 *
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.HttpClientEngineContainer)
 *
 * @property factory that produces HTTP client instances
 * @property priority that determines the order of searching for the default client engine.
 *   Higher values are preferred. When multiple containers share the same highest priority an exception is thrown;
 *   assign distinct priorities to avoid ambiguity.
 */
public interface HttpClientEngineContainer {
    public val factory: HttpClientEngineFactory<*>
    public val priority: Double get() = 1.0
}

@OptIn(InternalAPI::class)
private val FACTORY: HttpClientEngineFactory<*> by lazy {
    val engineContainers = loadServices<HttpClientEngineContainer>()
    when (engineContainers.size) {
        0 -> error(
            "Failed to find HTTP client engine implementation: consider adding client engine dependency. " +
                "See https://ktor.io/docs/http-client-engines.html"
        )

        1 -> engineContainers.single().factory

        else -> {
            val maxPriority = engineContainers.maxOf { it.priority }
            val topEngines = engineContainers.filter { it.priority == maxPriority }
            if (topEngines.size > 1) {
                error(
                    "Multiple HTTP client engine implementations share the same highest priority ($maxPriority): " +
                        topEngines.joinToString { it.factory::class.qualifiedName ?: it.factory.toString() } +
                        ". Specify an engine explicitly or assign distinct priorities to resolve the conflict. " +
                        "See https://ktor.io/docs/http-client-engines.html"
                )
            }
            topEngines.single().factory
        }
    }
}
