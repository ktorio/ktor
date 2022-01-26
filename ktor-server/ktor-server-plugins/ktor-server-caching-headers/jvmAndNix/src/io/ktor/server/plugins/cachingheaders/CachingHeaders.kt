/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.cachingheaders

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*

/**
 * A configuration for the [CachingHeaders] plugin
 */
public class CachingHeadersConfig {
    internal val optionsProviders = mutableListOf<(OutgoingContent) -> CachingOptions?>()

    init {
        optionsProviders.add { content -> content.caching }
    }

    /**
     * Registers a function that can provide caching options for a given [OutgoingContent]
     */
    public fun options(provider: (OutgoingContent) -> CachingOptions?) {
        optionsProviders.add(provider)
    }
}

/**
 * A plugin that adds the capability to configure the Cache-Control and Expires headers using [CachingOptions].
 * It invokes [CachingHeadersConfig.optionsProviders] for every response and use first non-null [CachingOptions]
 */
public val CachingHeaders: RouteScopedPlugin<CachingHeadersConfig, PluginInstance> = createRouteScopedPlugin(
    "Caching Headers",
    ::CachingHeadersConfig
) {

    val optionsProviders = pluginConfig.optionsProviders.toList()

    fun optionsFor(content: OutgoingContent): List<CachingOptions> {
        return optionsProviders.mapNotNullTo(ArrayList(optionsProviders.size)) { it(content) }
    }

    onCallRespond.afterTransform { call, message ->
        val options = if (message is OutgoingContent) optionsFor(message) else emptyList()

        if (options.isNotEmpty()) {
            val headers = Headers.build {
                options.mapNotNull { it.cacheControl }
                    .mergeCacheControlDirectives()
                    .ifEmpty { null }?.let { directives ->
                        append(HttpHeaders.CacheControl, directives.joinToString(separator = ", "))
                    }
                options.firstOrNull { it.expires != null }?.expires?.let { expires ->
                    append(HttpHeaders.Expires, expires.toHttpDate())
                }
            }

            val responseHeaders = call.response.headers
            headers.forEach { name, values ->
                values.forEach { responseHeaders.append(name, it) }
            }
        }
    }
}
