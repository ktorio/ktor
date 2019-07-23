/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine

import io.ktor.client.*
import io.ktor.client.response.*
import io.ktor.http.*
import io.ktor.util.*
import kotlinx.coroutines.*

/**
 * Base configuration for [HttpClientEngine].
 */
@HttpClientDsl
open class HttpClientEngineConfig {
    /**
     * The [CoroutineDispatcher] that will be used for the client requests.
     */
    @Deprecated(
        "Binary compatibility.",
        level = DeprecationLevel.HIDDEN
    )
    var dispatcher: CoroutineDispatcher?
        get() = null
        set(_) {
            throw UnsupportedOperationException("Custom dispatcher is deprecated. Use threadsCount instead.")
        }

    /**
     * Network threads count advice.
     */
    @KtorExperimentalAPI
    var threadsCount: Int = 4

    /**
     * Enable http pipelining advice.
     */
    @KtorExperimentalAPI
    var pipelining: Boolean = false

    /**
     * Configuration for http response.
     */
    val response: HttpResponseConfig = HttpResponseConfig()

    /**
     * Proxy address to use. Use system proxy by default.
     *
     * See [ProxyBuilder] to create proxy.
     */
    @KtorExperimentalAPI
    var proxy: ProxyConfig? = null
}
