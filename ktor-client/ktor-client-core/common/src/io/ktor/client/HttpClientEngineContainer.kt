/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client

import io.ktor.client.engine.*
import io.ktor.util.logging.KtorSimpleLogger
import io.ktor.utils.io.*

private const val NO_ENGINE_MESSAGE =
    "Failed to find HTTP client engine implementation: consider adding client engine dependency. " +
        "See https://ktor.io/docs/http-client-engines.html"

/**
 * A container that provides an HTTP client [factory] used to discover the default engine for [HttpClient].
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.HttpClientEngineContainer)
 *
 * @property factory that produces HTTP client instances
 * @property priority determines the order of searching for the default client engine.
 *   Higher values are preferred. When multiple containers share the same highest priority,
 *   the first discovered container is used and a warning is logged.
 */
public interface HttpClientEngineContainer {
    public val factory: HttpClientEngineFactory<*>
    public val priority: Double get() = 1.0
}

internal fun selectDefaultHttpClientEngine(
    engineContainers: List<HttpClientEngineContainer>
): HttpClientEngineFactory<*> {
    return when (engineContainers.size) {
        0 -> error(NO_ENGINE_MESSAGE)

        1 -> engineContainers.single().factory

        else -> {
            val enginesSorted = engineContainers.sortedByDescending { it.priority }
            val maxPriority = enginesSorted.first().priority
            val topEngines = enginesSorted.takeWhile { it.priority.compareTo(maxPriority) == 0 }
            val selectedEngine = topEngines.first()
            val logger = KtorSimpleLogger("HttpClient")
            if (topEngines.size > 1) {
                logger.warn(
                    buildString {
                        append("Multiple engines found: ")
                        appendLine(topEngines.joinToString { it.name })
                        appendLine("\tUsing the first: ${selectedEngine.name}")
                    }
                )
            } else {
                logger.info(
                    buildString {
                        append("Multiple engines found: ")
                        appendLine(engineContainers.joinToString { it.name })
                        appendLine(
                            "\tUsing the engine with the highest priority: ${selectedEngine.name} (priority: $maxPriority)"
                        )
                    }
                )
            }
            selectedEngine.factory
        }
    }
}

private val HttpClientEngineContainer.name get() = factory::class.simpleName ?: factory.toString()
